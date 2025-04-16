package io.flow.dependency.www.lib

import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.Client
import play.api.{Configuration, Environment, Mode}
import play.api.inject.Module

class DependencyClientProviderModule extends Module {

  def bindings(env: Environment, conf: Configuration) = {
    env.mode match {
      case Mode.Prod | Mode.Dev =>
        Seq(
          bind[DependencyClientProvider].to[DefaultDependencyClientProvider],
        )
      case Mode.Test =>
        Seq(
          bind[DependencyClientProvider].toInstance(new DependencyClientProvider {
            override def newClient(user: Option[UserReference], requestId: Option[String]): Client =
              sys.error("Not implemented") // TODO: Add mock
          }),
        )
    }
  }

}
