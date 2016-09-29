package com.bryzek.dependency.www.lib

import io.flow.token.v0.interfaces.{Client => TokenClient}
import play.api.{Environment, Configuration, Mode}
import play.api.inject.Module

class DependencyClientProviderModule extends Module {

  def bindings(env: Environment, conf: Configuration) = {
    env.mode match {
      case Mode.Prod | Mode.Dev => Seq(
        bind[DependencyClientProvider].to[DefaultDependencyClientProvider],
        bind[TokenClient].to[DefaultTokenClient]
      )
      case Mode.Test => Seq(
        // TODO: Add mock
        bind[DependencyClientProvider].to[DependencyClientProvider],
        bind[TokenClient].to[DefaultTokenClient]
      )
    }
  }

}
