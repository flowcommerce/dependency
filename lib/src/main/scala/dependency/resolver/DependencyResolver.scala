package dependency.resolver

import scala.annotation.tailrec

case class DependencyResolution(
  resolved: List[Seq[ProjectInfo]],
  unresolved: Seq[ProjectInfo],
) {

  private[this] lazy val allResolvedLibraries: Set[String] = resolved.flatMap(_.flatMap { p => p.provides.map(_.identifier) }).toSet

  def isLibraryResolved(identifier: String): Boolean = allResolvedLibraries.contains(identifier)

  def allUnknownLibraries: Seq[LibraryReference] = resolved.flatMap(_.flatMap(_.unknownLibraries)).distinct.sortBy(_.identifier)

}

object DependencyResolution {
  val empty: DependencyResolution = DependencyResolution(resolved = Nil, unresolved = Nil)
}

/**
 * @param groupId e.g. io.flow
 * @param artifactId e.g. lib-s3
 */
case class LibraryReference(groupId: String, artifactId: String) {
  val identifier: String = s"$groupId.$artifactId"
}

case class ProjectInfo(
  projectId: String,
  projectName: String,
  dependsOn: Seq[LibraryReference],
  provides: Seq[LibraryReference],
  resolvedDependencies: Seq[LibraryReference] = Nil,
  unresolvedDependencies: Seq[LibraryReference] = Nil,
  unknownLibraries: Seq[LibraryReference] = Nil,
)

/**
 * Given a list of projects, resolves the dependencies among them
 * to return an ordered list of sets of projects that can be upgraded
 * at one time.
 */
case class DependencyResolver(projects: Seq[ProjectInfo]) {

  lazy val resolution: DependencyResolution = resolve(DependencyResolution.empty, projects.toList)

  private[this] def areDependenciesSatisfied(resolution: DependencyResolution, libraries: Seq[LibraryReference]): Boolean = {
    libraries.forall { l =>
      resolution.isLibraryResolved(l.identifier)
    }
  }

  private[this] def hasMatchingProject(libraryReference: LibraryReference): Boolean = {
    projects.exists { p =>
      libraryReference.artifactId.startsWith(p.projectName)
    }
  }

  @tailrec
  private[this] def resolve(resolution: DependencyResolution, remainingProjects: List[ProjectInfo]): DependencyResolution = {
    val (resolved, remaining) = remainingProjects.partition { p =>
      areDependenciesSatisfied(resolution, p.dependsOn)
    }

    resolved match {
      case Nil => {
        resolution.copy(
          unresolved = resolution.unresolved ++ remaining.map { p =>
            val (resolvedDependencies, allUnresolved) = p.dependsOn.partition { l =>
              areDependenciesSatisfied(resolution, Seq(l))
            }
            val (unresolvedDependencies, unknownLibraries) = allUnresolved.partition(hasMatchingProject)

            p.copy(
              resolvedDependencies = resolvedDependencies,
              unresolvedDependencies = unresolvedDependencies,
              unknownLibraries = unknownLibraries,
            )
          },
        )
      }
      case _ => {
        resolve(resolution.copy(
          resolved = resolution.resolved ++ Seq(resolved),
        ), remaining)
      }
    }
  }
}
