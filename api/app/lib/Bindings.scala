package com.bryzek.dependency.api.lib

import io.flow.delta.api.lib.DefaultTokenClient
import io.flow.token.v0.interfaces.{Client => TokenClient}
import play.api.{Configuration, Environment, Mode}
import play.api.inject.Module

class TokenClientModule extends Module {

  def bindings(env: Environment, conf: Configuration) = {
    Seq(
      bind[TokenClient].to[DefaultTokenClient]
    )
  }

}

class GithubModule extends Module {

  def bindings(env: Environment, conf: Configuration) = {
    env.mode match {
      case Mode.Prod | Mode.Dev => Seq(
        bind[Github].to[DefaultGithub]
      )
      case Mode.Test => Seq(
        bind[Github].to[MockGithub]
      )
    }
  }

}
