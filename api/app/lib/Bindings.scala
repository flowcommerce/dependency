package io.flow.dependency.api.lib

import play.api.inject.Module
import play.api.{Configuration, Environment, Mode}

class GithubModule extends Module {

  def bindings(env: Environment, conf: Configuration) = {
    env.mode match {
      case Mode.Prod | Mode.Dev =>
        Seq(
          bind[Github].to[DefaultGithub],
        )
      case Mode.Test =>
        Seq(
          bind[Github].to[MockGithub],
        )
    }
  }

}
