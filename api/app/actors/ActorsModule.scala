package io.flow.dependency.actors

import actors.TaskExecutorActor
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class ActorsModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bindActor[BinaryActor]("binary-actor")
    bindActor[EmailActor]("email-actor")
    bindActor[ProjectActor]("project-actor")
    bindActor[ResolverActor]("resolver-actor")
    bindActor[SearchActor]("search-actor")
    bindActor[TaskActor]("task-actor")
    bindActor[TaskExecutorActor]("task-executor-actor")
  }
}
