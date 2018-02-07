package io.flow.dependency.www.lib

import io.flow.play.clients.DefaultTokenClient
import io.flow.token.v0.interfaces.{Client => TokenClient}
import play.api.{Configuration, Environment, Mode}
import play.api.inject.Module

class DependencyClientProviderModule extends Module {

  def bindings(env: Environment, conf: Configuration) = {
    env.mode match {
      case Mode.Prod | Mode.Dev => Seq(
        bind[DependencyClientProvider].to[DefaultDependencyClientProvider]
      )
      case Mode.Test => Seq(
        // TODO: Add mock
        bind[DependencyClientProvider].to[DependencyClientProvider]
      )
    }
  }

}
