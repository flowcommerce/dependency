package dependency.resolver

import scala.annotation.tailrec

case class DependencyResolution(
  resolved: List[Seq[ProjectInfo]],
  unresolved: Seq[ProjectInfo],
) {

  private[this] lazy val allResolvedLibraries: Set[String] = resolved.flatMap(_.flatMap { p => p.provides.map(_.identifier) }).toSet

  def isLibraryResolved(identifier: String): Boolean = allResolvedLibraries.contains(identifier)

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
  dependsOn: Seq[LibraryReference],
  provides: Seq[LibraryReference],
  resolvedDependencies: Seq[LibraryReference] = Nil,
  unresolvedDependencies: Seq[LibraryReference] = Nil,
)

/**
 * Given a list of projects, resolves the dependencies among them
 * to return an ordered list of sets of projects that can be upgraded
 * at one time.
 */
case class DependencyResolver() {

  def resolve(projects: Seq[ProjectInfo]): DependencyResolution = {
    resolve(DependencyResolution.empty, projects.toList)
  }

  private[this] def areDependenciesSatisfied(resolution: DependencyResolution, libraries: Seq[LibraryReference]): Boolean = {
    libraries.forall { l =>
      resolution.isLibraryResolved(l.identifier)
    }
  }

  @tailrec
  private[this] def resolve(resolution: DependencyResolution, projects: List[ProjectInfo]): DependencyResolution = {
    val (resolved, remaining) = projects.partition { p =>
      areDependenciesSatisfied(resolution, p.dependsOn)
    }

    resolved match {
      case Nil => {
        resolution.copy(
          unresolved = resolution.unresolved ++ remaining.map { p =>
            val (r, u) = p.dependsOn.partition { l =>
              areDependenciesSatisfied(resolution, Seq(l))
            }
            p.copy(
              resolvedDependencies = r,
              unresolvedDependencies = u,
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
