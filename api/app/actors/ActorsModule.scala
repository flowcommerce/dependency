package io.flow.dependency.actors

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class ActorsModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bindActorFactory[ProjectActor, ProjectActor.Factory]
    bindActor[BinaryActor]("binary-actor")
    bindActor[MainActor]("main-actor")
  }
}
