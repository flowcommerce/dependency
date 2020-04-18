package lib.resolver

import helpers.resolver.ResolverHelpers
import io.flow.test.utils.FlowPlaySpec

class DependencyResolverSpec extends FlowPlaySpec with ResolverHelpers {

  private[this] def dependencyResolver: DependencyResolver = init[DependencyResolver]

  "no projects" in {
    dependencyResolver.resolve(Nil) must equal(DependencyResolution.empty)
  }

  "projects w/ no dependencies" in {
    val projects = Seq(makeProjectInfo(), makeProjectInfo())
    dependencyResolver.resolve(projects) must equal(
      DependencyResolution.empty.copy(
        resolved = List(projects)
      )
    )
  }
}
