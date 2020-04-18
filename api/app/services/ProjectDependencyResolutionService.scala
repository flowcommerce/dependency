package services

import com.google.inject.ImplementedBy
import db.{Authorization, LibrariesDao, ProjectLibrariesDao, ProjectsDao}
import dependency.resolver.{DependencyResolver, LibraryReference, ProjectInfo}
import io.flow.dependency.v0.models.{Library, Project, ProjectDependencyResolution, ProjectDependencyResolutionResolved, ProjectUnresolvedSummary}
import javax.inject.Inject

@ImplementedBy(classOf[ProjectDependencyResolutionServiceImpl])
trait ProjectDependencyResolutionService {
  def getByOrganizationId(organizationKey: String, groupId: String): ProjectDependencyResolution
}

class ProjectDependencyResolutionServiceImpl @Inject() (
  projectsDao: ProjectsDao,
  projectLibrariesDao: ProjectLibrariesDao,
  librariesDao: LibrariesDao,
) extends ProjectDependencyResolutionService {

  override def getByOrganizationId(organizationKey: String, groupId: String): ProjectDependencyResolution = {
    val allProjects = projects(organizationKey)
    val r = DependencyResolver().resolve(buildProjectInfo(allProjects.values.toSeq, groupId = groupId))

    def toSummary(projectId: String) = projectsDao.toSummary(allProjects(projectId))

    ProjectDependencyResolution(
      resolved = r.resolved.map { p =>
        ProjectDependencyResolutionResolved(
          projects = p.map(_.projectId).map(toSummary),
        )
      },
      unresolved = r.unresolved.map { p =>
        ProjectUnresolvedSummary(
          project = toSummary(p.projectId),
          resolvedLibraries = p.resolvedDependencies.map(_.identifier),
          unresolvedLibraries = p.unresolvedDependencies.map(_.identifier),
        )
      }
    )
  }

  // For the set of projects,
  //   1. find all dependent libraries
  //   2. find all libraries where the artifact starts with the projects id
  // and use that to build up the project info we need to resolve dependencies
  private[services] def buildProjectInfo(allProjects: Seq[Project], groupId: String): Seq[ProjectInfo] = {
    val allDependentLibraries = dependentLibraries(allProjects.map(_.id), groupId)
    val allLibraries = libraries(groupId)

    allProjects.map { p =>
      val dependsOn = allDependentLibraries.getOrElse(p.id, Nil)
      val provides = findProvides(p, allLibraries)
      println(s" - project[${p.name}]")
      println(s"    - provides: ${provides.map(_.identifier).mkString(", ")}")
      println(s"    - dependsOn: ${dependsOn.map(_.identifier).mkString(", ")}")
      ProjectInfo(
        projectId = p.id,
        dependsOn = dependsOn,
        provides = provides,
      )
    }
  }

  // Find all libraries where artifact id starts with project name
  private[this] def findProvides(project: Project, libraries: Seq[Library]): Seq[LibraryReference] = {
    libraries
      .filter(_.artifactId.toLowerCase().startsWith(project.name.toLowerCase()))
      .map { l => LibraryReference(l.groupId, l.artifactId) }
  }

  private[this] def dependentLibraries(projectIds: Seq[String], groupId: String): Map[String, Seq[LibraryReference]] = {
    projectLibrariesDao.findAll(
      Authorization.All,
      projectIds = Some(projectIds),
      groupId = Some(groupId),
      limit = None
    ).groupBy(_.project.id).map { case (pid, libs) =>
      pid -> libs.map { l => LibraryReference(l.groupId, l.artifactId) }
    }
  }

  private[this] def projects(organizationKey: String): Map[String, Project] = {
    projectsDao.findAll(
      Authorization.All,
      organizationKey = Some(organizationKey),
      limit = None,
    ).map { p => p.id -> p }.toMap
  }

  private[this] def libraries(groupId: String): Seq[Library] = {
    librariesDao.findAll(
      Authorization.All,
      groupId = Some(groupId),
      limit = None,
    )
  }

}
