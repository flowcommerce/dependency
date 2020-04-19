package services

import com.google.inject.ImplementedBy
import db.{Authorization, LibrariesDao, ProjectLibrariesDao, ProjectsDao}
import dependency.resolver.{DependencyResolver, LibraryReference, ProjectInfo}
import io.flow.dependency.v0.models.{Library, LibrarySummary, ProjectDependencyResolution, ProjectDependencyResolutionResolved, ProjectSummary, ProjectUnresolvedSummary}
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
    val allLibraries = libraries(groupId)
    lazy val allLibrariesByIdentifier = allLibraries.map { l =>
      LibraryReference(l.groupId, l.artifactId).identifier -> l
    }.toMap

    val r = DependencyResolver(
      buildProjectInfo(allProjects.values.toSeq, allLibraries, groupId = groupId)
    ).resolution

    ProjectDependencyResolution(
      resolved = r.resolved.map { p =>
        ProjectDependencyResolutionResolved(
          projects = p.map(_.projectId).map(allProjects),
        )
      },
      unresolved = r.unresolved.map { p =>
        ProjectUnresolvedSummary(
          project = allProjects(p.projectId),
          resolvedLibraries = p.resolvedDependencies.map(_.identifier),
          unresolvedLibraries = p.unresolvedDependencies.map(_.identifier),
        )
      },
      unknown = r.allUnknownLibraries.map { l =>
        allLibrariesByIdentifier(l.identifier)
      },
    )
  }

  // For the set of projects,
  //   1. find all dependent libraries
  //   2. find all libraries where the artifact starts with the projects id
  // and use that to build up the project info we need to resolve dependencies
  private[services] def buildProjectInfo(
    allProjects: Seq[ProjectSummary],
    allLibraries: Seq[LibrarySummary],
    groupId: String,
  ): Seq[ProjectInfo] = {
    val allDependentLibraries = dependentLibraries(allProjects.map(_.id), groupId)

    allProjects.map { p =>
      val dependsOn = allDependentLibraries.getOrElse(p.id, Nil)
      val provides = findProvides(p, allLibraries)
      println(s" - project[${p.name}]")
      println(s"    - provides: ${provides.map(_.identifier).mkString(", ")}")
      println(s"    - dependsOn: ${dependsOn.map(_.identifier).mkString(", ")}")
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
      limit = None
    ).groupBy(_.project.id).map { case (pid, libs) =>
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

  private[services] def libraries(groupId: String): Seq[LibrarySummary] = {
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
