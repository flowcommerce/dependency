package com.bryzek.dependency.actors

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class ActorsModule extends AbstractModule with AkkaGuiceSupport {
  def configure = {
    bindActorFactory[ProjectActor, ProjectActor.Factory]
    bindActor[MainActor]("main-actor")
  }
}
