package lib

import io.flow.dependency.api.lib.{DefaultGithub, Github, MockGithub}
import io.flow.lib.dependency.DependencyProjects
import play.api.{Configuration, Environment, Mode}
import play.api.inject.{Binding, Module}

class DependencyProjectsModule extends Module {

  def bindings(env: Environment, conf: Configuration): Seq[Binding[_]] = {
    Seq(bind[DependencyProjects].to[DaoBasedDependencyProjects])
  }

}
