package services

import com.google.inject.ImplementedBy
import db.{Authorization, LibrariesDao, InternalProjectLibrariesDao, ProjectsDao}
import dependency.resolver.{DependencyResolver, LibraryReference, ProjectInfo}
import io.flow.dependency.v0.models.{Library, LibrarySummary, ProjectDependencyResolution, ProjectDependencyResolutionResolved, ProjectSummary, ProjectUnresolvedSummary}
import javax.inject.Inject

@ImplementedBy(classOf[ProjectDependencyResolutionServiceImpl])
trait ProjectDependencyResolutionService {
  def getByOrganizationKey(organizationKey: String, groupId: String): ProjectDependencyResolution
}

class ProjectDependencyResolutionServiceImpl @Inject() (
  projectsDao: ProjectsDao,
  projectLibrariesDao: InternalProjectLibrariesDao,
  librariesDao: LibrariesDao,
) extends ProjectDependencyResolutionService {

  override def getByOrganizationKey(organizationKey: String, groupId: String): ProjectDependencyResolution = {
    val allProjects = projects(organizationKey)
    println(s" all pojects: ${allProjects.keys}")

    val r = DependencyResolver(
      buildProjectInfo(allProjects.values.toSeq, groupId = groupId)
    ).resolution

    val resolved = r.resolved.map { p =>
      ProjectDependencyResolutionResolved(
        projects = p.map(_.projectId).map(allProjects),
      )
    }

    ProjectDependencyResolution(
      resolved = resolved,
      steps = r.resolved.zipWithIndex.map { case (projects, index) =>
        (index+1).toString -> projects.map(_.projectName).sorted.mkString(" ")
      }.toMap,
      unresolved = r.unresolved.map { p =>
        ProjectUnresolvedSummary(
          project = allProjects(p.projectId),
          resolvedLibraries = p.resolvedDependencies.map(_.identifier),
          unresolvedLibraries = p.unresolvedDependencies.map(_.identifier),
        )
      },
    )
  }

  // For the set of projects,
  //   1. find all dependent libraries
  //   2. find all libraries where the artifact starts with the projects id
  // and use that to build up the project info we need to resolve dependencies
  private[services] def buildProjectInfo(
    allProjects: Seq[ProjectSummary],
    groupId: String,
  ): Seq[ProjectInfo] = {
    val allLibraries = libraries(groupId)
    val allDependentLibraries = dependentLibraries(allProjects.map(_.id), groupId)

    allProjects.map { p =>
      val dependsOn = allDependentLibraries.getOrElse(p.id, Nil).filterNot { l =>
        // project scala-fix depends on itself... so remove own project from depends on
        // to allow resolution to succeed
        l.groupId == groupId && l.artifactId == p.name
      }
      val provides = findProvides(p, allLibraries)
      ProjectInfo(
        projectId = p.id,
        projectName = p.name,
        dependsOn = dependsOn,
        provides = provides,
      )
    }
  }

  // Find all libraries where artifact id starts with project name
  private[this] def findProvides(project: ProjectSummary, libraries: Seq[LibrarySummary]): Seq[LibraryReference] = {
    libraries
      .filter { l => ArtifactNameMatcher.matches(l.artifactId, project.name) }
      .map { l => LibraryReference(l.groupId, l.artifactId) }
  }

  private[this] def dependentLibraries(projectIds: Seq[String], groupId: String): Map[String, Seq[LibraryReference]] = {
    projectLibrariesDao.findAll(
      Authorization.All,
      projectIds = Some(projectIds),
      groupId = Some(groupId),
      limit = None,
      orderBy = None,
    ).groupBy(_.projectId).map { case (pid, libs) =>
      pid -> libs.map { l => LibraryReference(l.groupId, l.artifactId) }
    }
  }

  private[this] def projects(organizationKey: String): Map[String, ProjectSummary] = {
    projectsDao.findAll(
      Authorization.All,
      organizationKey = Some(organizationKey),
      limit = None,
    ).map { p => p.id -> projectsDao.toSummary(p) }.toMap
  }

  private[this] def libraries(groupId: String): Seq[LibrarySummary] = {
    librariesDao.findAll(
      Authorization.All,
      groupId = Some(groupId),
      limit = None,
    ).map(toLibrarySummary)
  }

  private[this] def toLibrarySummary(library: Library): LibrarySummary = {
    LibrarySummary(
      id = library.id,
      organization = library.organization,
      groupId = library.groupId,
      artifactId = library.artifactId,
    )
  }
}
