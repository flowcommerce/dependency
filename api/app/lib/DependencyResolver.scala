package io.flow.dependency.api.lib

import io.flow.dependency.v0.models.Project
import javax.inject.Inject

import scala.annotation.tailrec

case class DependencyResolution(
  resolved: List[Seq[Project]],
  circular: Seq[Project],
)
object DependencyResolution {
  val empty: DependencyResolution = DependencyResolution(resolved = Nil, circular = Nil)
}

/**
 * Given a list of projects, resolves the dependencies among them
 * to return an ordered list of sets of projects to upgrade at
 * one time.
 */
class DependencyResolver @Inject() () {

  def resolve(projects: Seq[Project]): DependencyResolution = {
    resolve(DependencyResolution.empty, projects.toList)
  }

  @tailrec
  private[this] def resolve(resolution: DependencyResolution, projects: List[Project]): DependencyResolution = {
    projects match {
      case Nil => resolution
      case p :: rest => {
        resolve(resolution.copy(
          unresolved = resolution.unresolved ++ Seq(p)
        ), rest)
      }
    }
  }
}
