package lib

import io.flow.lib.dependency.clients.DependencyProjects
import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}

class DependencyProjectsModule extends Module {

  def bindings(env: Environment, conf: Configuration): Seq[Binding[_]] = {
    Seq(bind[DependencyProjects].to[DaoBasedDependencyProjects])
  }

}
