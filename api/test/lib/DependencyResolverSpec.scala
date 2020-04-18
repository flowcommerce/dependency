package io.flow.dependency.api.lib

import io.flow.test.utils.FlowPlaySpec
import util.Factories

class DependencyResolverSpec extends FlowPlaySpec with Factories {

  def dependencyResolver = init[DependencyResolver]

  "no projects" in {
    dependencyResolver.resolve(Nil) must equal(DependencyResolution.empty)
  }

  "single projects" in {
    dependencyResolver.resolve(Nil) must equal(DependencyResolution.empty)
  }
}
