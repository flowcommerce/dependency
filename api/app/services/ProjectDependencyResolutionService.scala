package services

import com.google.inject.ImplementedBy
import db.{Authorization, LibrariesDao, ProjectLibrariesDao, ProjectsDao}
import dependency.resolver.{DependencyResolver, LibraryReference, ProjectInfo}
import io.flow.dependency.v0.models.{Library, Project, ProjectDependencyResolution, ProjectDependencyResolutionResolved, Reference}
import javax.inject.Inject

@ImplementedBy(classOf[ProjectDependencyResolutionServiceImpl])
trait ProjectDependencyResolutionService {
  def getByOrganizationId(organizationId: String): ProjectDependencyResolution
}

class ProjectDependencyResolutionServiceImpl @Inject() (
  projectsDao: ProjectsDao,
  projectLibrariesDao: ProjectLibrariesDao,
  librariesDao: LibrariesDao,
) extends ProjectDependencyResolutionService {

  override def getByOrganizationId(organizationId: String): ProjectDependencyResolution = {
    val allProjects = projects(organizationId)
    val r = DependencyResolver().resolve(buildProjectInfo(allProjects.values.toSeq))

    def toSummary(projectId: String) = projectsDao.toSummary(allProjects(projectId))

    ProjectDependencyResolution(
      resolved = r.resolved.zipWithIndex.map { case (p, pos) =>
        ProjectDependencyResolutionResolved(
          position = pos+1L,
          projects = p.map(_.projectId).map(toSummary),
        )
      },
      circular = r.circular.map(_.projectId).map(toSummary),
    )
  }

  private[this] def buildProjectInfo(allProjects: Seq[Project]): Seq[ProjectInfo] = {
    val allDependentLibraries = dependentLibraries(allProjects.map(_.id))
    val allLibraries = libraries(allDependentLibraries.values.flatMap(_.map(_.libraryId)).toSeq.distinct)
    allProjects.map { p =>
      ProjectInfo(
        projectId = p.id,
        dependsOn = allDependentLibraries.getOrElse(p.id, Nil),
        provides = findProvides(p, allLibraries),
      )
    }
  }

  // Find all libraries where artifact id starts with project id
  private[this] def findProvides(project: Project, libraries: Seq[Library]): Seq[LibraryReference] = {
    libraries.filter(_.artifactId.startsWith(project.id)).map { l => LibraryReference(l.id) }
  }


  private[this] def dependentLibraries(projectIds: Seq[String]): Map[String, Seq[LibraryReference]] = {
    projectLibrariesDao.findAll(
      Authorization.All,
      projectIds = Some(projectIds),
      limit = None
    ).groupBy(_.project.id).map { case (pid, libs) =>
      pid -> libs.flatMap(_.library.map(toLibraryReference))
    }
  }

  private[this] def projects(organizationId: String): Map[String, Project] = {
    projectsDao.findAll(
      Authorization.All,
      organizationId = Some(organizationId),
      limit = None,
    ).map { p => p.id -> p }.toMap
  }

  private[this] def libraries(libraryIds: Seq[String]): Seq[Library] = {
    librariesDao.findAll(
      Authorization.All,
      ids = Some(libraryIds),
      limit = None,
    )
  }

  private[this] def toLibraryReference(library: Reference): LibraryReference = LibraryReference(library.id)

}
