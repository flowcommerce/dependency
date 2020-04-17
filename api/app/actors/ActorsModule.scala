package io.flow.dependency.actors

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class ActorsModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bindActor[BinaryActor]("binary-actor")
    bindActor[EmailActor]("email-actor")
    bindActor[LibraryActor]("library-actor")
    bindActor[PeriodicActor]("periodic-actor")
    bindActor[ProjectActor]("project-actor")
    bindActor[ResolverActor]("resolver-actor")
    bindActor[SearchActor]("search-actor")
    bindActor[UserActor]("user-actor")

    bindActor[TaskActor]("task-actor")
    bindActor[TaskActorUpserted]("task-actor-upserted")
    bindActor[TaskActorSyncAll]("task-actor-sync-all")
    bindActor[TaskActorSyncOneBinary]("task-actor-sync-one-binary")
    bindActor[TaskActorSyncOneLibrary]("task-actor-sync-one-library")
    bindActor[TaskActorSyncOrganizationLibraries]("task-actor-sync-organization-libraries")
    bindActor[TaskActorSyncOneProject]("task-actor-sync-one-project")
  }
}
