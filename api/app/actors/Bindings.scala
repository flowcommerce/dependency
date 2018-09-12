package io.flow.dependency.actors

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class ActorsModule extends AbstractModule with AkkaGuiceSupport {
  override def configure = {
    bindActorFactory[ProjectActor, ProjectActor.Factory]
    bindActor[MainActor]("main-actor")
  }
}
