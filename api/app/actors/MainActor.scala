package io.flow.dependency.actors

import db._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import io.flow.log.RollbarLogger
import io.flow.akka.SafeReceive
import io.flow.akka.recurring.{ScheduleConfig, Scheduler}
import io.flow.play.util.ApplicationConfig
import play.api.libs.concurrent.InjectedActorSupport

import scala.concurrent.ExecutionContext

object MainActor {

  object Messages {

    case class ProjectLibraryCreated(projectId: String, id: String)
    case class ProjectLibrarySync(projectId: String, id: String)
    case class ProjectLibraryDeleted(projectId: String, id: String, version: String)

    case class ProjectBinaryCreated(projectId: String, id: String)
    case class ProjectBinarySync(projectId: String, id: String)
    case class ProjectBinaryDeleted(projectId: String, id: String, version: String)

    case class ResolverCreated(id: String)
    case class ResolverDeleted(id: String)

    case class UserCreated(id: String)
  }
}

@javax.inject.Singleton
class MainActor @javax.inject.Inject() (
  logger: RollbarLogger,
  config: ApplicationConfig,
  system: ActorSystem,
  projectFactory: ProjectActor.Factory,
  usersDao: UsersDao,
  organizationsDao: OrganizationsDao,
  userIdentifiersDao: UserIdentifiersDao,
  subscriptionsDao: SubscriptionsDao,
  librariesDao: LibrariesDao,
  resolversDao: ResolversDao,
  projectLibrariesDao: ProjectLibrariesDao,
  batchEmailProcessor: BatchEmailProcessor
) extends Actor with ActorLogging with Scheduler with InjectedActorSupport {

  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)
  private[this] val name = "main"

  private[this] val emailActor = system.actorOf(Props(new EmailActor(
    subscriptionsDao,
    batchEmailProcessor,
    config,
    logger
  )), name = s"$name:emailActor")

  private[this] val projectActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val userActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val resolverActors = scala.collection.mutable.Map[String, ActorRef]()

  private[this] implicit val ec: ExecutionContext = system.dispatchers.lookup("main-actor-context")

  scheduleRecurring(
    ScheduleConfig.fromConfig(config.underlying.underlying, "io.flow.dependency.api.email"),
    EmailActor.Messages.ProcessDailySummary,
    emailActor,
  )

  def receive: Receive = SafeReceive.withLogUnhandled {

    case MainActor.Messages.UserCreated(id) =>
      upsertUserActor(id) ! UserActor.Messages.Created

    case MainActor.Messages.ProjectLibraryCreated(projectId, id) =>
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectLibraryCreated(id)

    case MainActor.Messages.ProjectLibrarySync(projectId, id) =>
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectLibrarySync(id)

    case MainActor.Messages.ProjectLibraryDeleted(projectId, id, version) =>
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectLibraryDeleted(id, version)

    case MainActor.Messages.ProjectBinaryCreated(projectId, id) =>
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectBinaryCreated(id)

    case MainActor.Messages.ProjectBinarySync(projectId, id) =>
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectBinarySync(id)

    case MainActor.Messages.ProjectBinaryDeleted(projectId, id, version) =>
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectBinaryDeleted(id, version)

    case MainActor.Messages.ResolverCreated(id) =>
      upsertResolverActor(id) ! ResolverActor.Messages.Sync

    case MainActor.Messages.ResolverDeleted(id) =>
      resolverActors.remove(id).foreach { ref =>
        ref ! ResolverActor.Messages.Deleted
      }
  }

  def upsertUserActor(id: String): ActorRef = {
    userActors.lift(id).getOrElse {
      val ref = system.actorOf(Props(new UserActor(
        organizationsDao,
        userIdentifiersDao,
        subscriptionsDao,
        usersDao,
        logger
      )), name = s"$name:userActor:$id")
      ref ! UserActor.Messages.Data(id)
      userActors += (id -> ref)
      ref
    }
  }

  def upsertProjectActor(id: String): ActorRef = {
    projectActors.lift(id).getOrElse {
      val ref = injectedChild(projectFactory(id), name = s"$name:projectActor:$id")
      projectActors += (id -> ref)
      ref
    }
  }

  def upsertResolverActor(id: String): ActorRef = {
    resolverActors.lift(id).getOrElse {
      val ref = system.actorOf(Props(new ResolverActor(
        resolversDao,
        librariesDao,
        projectLibrariesDao,
        usersDao,
        logger
      )), name = s"$name:resolverActor:$id")
      ref ! ResolverActor.Messages.Data(id)
      resolverActors += (id -> ref)
      ref
    }
  }
}
