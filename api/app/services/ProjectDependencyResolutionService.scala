package services

import com.google.inject.ImplementedBy
import db.{Authorization, LibrariesDao, ProjectLibrariesDao, ProjectsDao}
import dependency.resolver.{DependencyResolver, LibraryReference, ProjectInfo}
import io.flow.dependency.v0.models.{Library, Project, ProjectDependencyResolution, ProjectDependencyResolutionResolved}
import javax.inject.Inject

@ImplementedBy(classOf[ProjectDependencyResolutionServiceImpl])
trait ProjectDependencyResolutionService {
  def getByOrganizationId(organizationKey: String): ProjectDependencyResolution
}

class ProjectDependencyResolutionServiceImpl @Inject() (
  projectsDao: ProjectsDao,
  projectLibrariesDao: ProjectLibrariesDao,
  librariesDao: LibrariesDao,
) extends ProjectDependencyResolutionService {

  override def getByOrganizationId(organizationKey: String): ProjectDependencyResolution = {
    val allProjects = projects(organizationKey)
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

  // For the set of projects,
  //   1. find all dependent libraries
  //   2. find all libraries where the artifact starts with the projects id
  // and use that to build up the project info we need to resolve dependencies
  private[services] def buildProjectInfo(allProjects: Seq[Project]): Seq[ProjectInfo] = {
    val allDependentLibraries = dependentLibraries(allProjects.map(_.id))
    val allLibraries = libraries()

    allProjects.map { p =>
      ProjectInfo(
        projectId = p.id,
        dependsOn = allDependentLibraries.getOrElse(p.id, Nil),
        provides = findProvides(p, allLibraries),
      )
    }
  }

  // Find all libraries where artifact id starts with project name
  private[this] def findProvides(project: Project, libraries: Seq[Library]): Seq[LibraryReference] = {
    println(s" - project name: ${project.name}")
    println(s" - library artifacts: ${libraries.map(_.artifactId)}")
    libraries.filter(_.artifactId.toLowerCase().startsWith(project.name.toLowerCase())).map { l =>
      toLibraryReference(l.groupId, l.artifactId)
    }
  }

  private[this] def dependentLibraries(projectIds: Seq[String]): Map[String, Seq[LibraryReference]] = {
    projectLibrariesDao.findAll(
      Authorization.All,
      projectIds = Some(projectIds),
      limit = None
    ).groupBy(_.project.id).map { case (pid, libs) =>
      pid -> libs.map { l => toLibraryReference(l.groupId, l.artifactId) }
    }
  }

  private[this] def projects(organizationKey: String): Map[String, Project] = {
    projectsDao.findAll(
      Authorization.All,
      organizationKey = Some(organizationKey),
      limit = None,
    ).map { p => p.id -> p }.toMap
  }

  private[this] def libraries(): Seq[Library] = {
    librariesDao.findAll(
      Authorization.All,
      limit = None,
    )
  }

  private[this] def toLibraryReference(groupId: String, artifactId: String): LibraryReference = {
    LibraryReference(identifier = s"${groupId}.${artifactId}")
  }

}
