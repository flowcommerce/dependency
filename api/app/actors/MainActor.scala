package io.flow.dependency.actors

import db._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import io.flow.dependency.api.lib.DefaultBinaryVersionProvider
import io.flow.log.RollbarLogger
import io.flow.akka.SafeReceive
import io.flow.akka.recurring.{ScheduleConfig, Scheduler}
import io.flow.play.util.ApplicationConfig
import io.flow.postgresql.Pager
import play.api.libs.concurrent.InjectedActorSupport

import scala.concurrent.ExecutionContext

object MainActor {

  object Messages {

    case class ProjectCreated(id: String)
    case class ProjectUpdated(id: String)
    case class ProjectDeleted(id: String)
    case class ProjectSync(id: String)

    case object SyncAll

    case class ProjectLibraryCreated(projectId: String, id: String)
    case class ProjectLibrarySync(projectId: String, id: String)
    case class ProjectLibraryDeleted(projectId: String, id: String, version: String)

    case class ProjectBinaryCreated(projectId: String, id: String)
    case class ProjectBinarySync(projectId: String, id: String)
    case class ProjectBinaryDeleted(projectId: String, id: String, version: String)

    case class ResolverCreated(id: String)
    case class ResolverDeleted(id: String)

    case class LibraryCreated(id: String)
    case class LibraryDeleted(id: String)
    case class LibrarySync(id: String)
    case class LibrarySyncFuture(id: String, seconds: Int)
    case class LibrarySyncCompleted(id: String)

    case class LibraryVersionCreated(id: String, libraryId: String)
    case class LibraryVersionDeleted(id: String, libraryId: String)

    case class BinaryCreated(id: String)
    case class BinaryDeleted(id: String)
    case class BinarySync(id: String)
    case class BinarySyncCompleted(id: String)

    case class BinaryVersionCreated(id: String, binaryId: String)
    case class BinaryVersionDeleted(id: String, binaryId: String)
    
    case class UserCreated(id: String)
  }
}

@javax.inject.Singleton
class MainActor @javax.inject.Inject() (
  logger: RollbarLogger,
  config: ApplicationConfig,
  system: ActorSystem,
  projectFactory: ProjectActor.Factory,
  binariesDao: BinariesDao,
  syncsDao: SyncsDao,
  binaryVersionsDao: BinaryVersionsDao,
  usersDao: UsersDao,
  itemsDao: ItemsDao,
  projectBinariesDao: ProjectBinariesDao,
  organizationsDao: OrganizationsDao,
  userIdentifiersDao: UserIdentifiersDao,
  subscriptionsDao: SubscriptionsDao,
  librariesDao: LibrariesDao,
  resolversDao: ResolversDao,
  libraryVersionsDao: LibraryVersionsDao,
  projectLibrariesDao: ProjectLibrariesDao,
  batchEmailProcessor: BatchEmailProcessor,
  projectsDao: ProjectsDao,
  defaultBinaryVersionProvider: DefaultBinaryVersionProvider
) extends Actor with ActorLogging with Scheduler with InjectedActorSupport {

  import scala.concurrent.duration._

  private[this] implicit val configuredRollbar = logger.fingerprint("MainActor")
  private[this] val name = "main"

  private[this] val emailActor = system.actorOf(Props(new EmailActor(
    subscriptionsDao,
    batchEmailProcessor,
    config,
    logger
  )), name = s"$name:emailActor")
  private[this] val periodicActor = system.actorOf(Props(new PeriodicActor(
    syncsDao,
    projectsDao,
    binariesDao,
    librariesDao,
    logger
  )), name = s"$name:periodicActor")
  private[this] val searchActor = system.actorOf(Props(new SearchActor(
    binariesDao: BinariesDao,
    librariesDao,
    projectsDao,
    itemsDao,
    usersDao,
    logger
  )), name = s"$name:SearchActor")

  private[this] val binaryActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val libraryActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val projectActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val userActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val resolverActors = scala.collection.mutable.Map[String, ActorRef]()

  implicit val mainActorExecutionContext: ExecutionContext = system.dispatchers.lookup("main-actor-context")

  scheduleRecurring(
    ScheduleConfig.fromConfig(config.underlying.underlying, "io.flow.dependency.api.binary"),
    PeriodicActor.Messages.SyncBinaries,
    periodicActor,
  )

  scheduleRecurring(
    ScheduleConfig.fromConfig(config.underlying.underlying, "io.flow.dependency.api.library"),
    PeriodicActor.Messages.SyncLibraries,
    periodicActor,
  )

  scheduleRecurring(
    ScheduleConfig.fromConfig(config.underlying.underlying, "io.flow.dependency.api.project"),
    PeriodicActor.Messages.SyncProjects,
    periodicActor,
  )

  scheduleRecurring(
    ScheduleConfig.fromConfig(config.underlying.underlying, "io.flow.dependency.api.purge"),
    PeriodicActor.Messages.Purge,
    periodicActor,
  )

  scheduleRecurring(
    ScheduleConfig.fromConfig(config.underlying.underlying, "io.flow.dependency.api.email"),
    EmailActor.Messages.ProcessDailySummary,
    emailActor,
  )

  def receive = SafeReceive.withLogUnhandled {

    case MainActor.Messages.UserCreated(id) =>
      upsertUserActor(id) ! UserActor.Messages.Created

    case MainActor.Messages.ProjectCreated(id) =>
      val actor = upsertProjectActor(id)
      actor ! ProjectActor.Messages.CreateHooks
      actor ! ProjectActor.Messages.Sync
      searchActor ! SearchActor.Messages.SyncProject(id)

    case MainActor.Messages.ProjectUpdated(id) =>
      upsertProjectActor(id) ! ProjectActor.Messages.Sync
      searchActor ! SearchActor.Messages.SyncProject(id)

    case MainActor.Messages.ProjectDeleted(id) =>
      projectActors.remove(id).map { actor =>
        actor ! ProjectActor.Messages.Deleted
      }
      searchActor ! SearchActor.Messages.SyncProject(id)

    case MainActor.Messages.ProjectSync(id) =>
      upsertProjectActor(id) ! ProjectActor.Messages.Sync
      searchActor ! SearchActor.Messages.SyncProject(id)

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

    case MainActor.Messages.LibraryCreated(id) =>
      syncLibrary(id)

    case MainActor.Messages.LibrarySync(id) =>
      syncLibrary(id)

    case MainActor.Messages.LibrarySyncFuture(id, seconds) =>
      system.scheduler.scheduleOnce(Duration(seconds.toLong, "seconds")) {
        syncLibrary(id)
      }
      ()

    case MainActor.Messages.LibrarySyncCompleted(id) =>
      projectBroadcast(ProjectActor.Messages.LibrarySynced(id))

    case MainActor.Messages.LibraryDeleted(id) =>
      libraryActors.remove(id).foreach { ref =>
        ref ! LibraryActor.Messages.Deleted
      }

    case MainActor.Messages.LibraryVersionCreated(_, libraryId) =>
      syncLibraryVersion(libraryId)

    case MainActor.Messages.LibraryVersionDeleted(_, libraryId) =>
      syncLibraryVersion(libraryId)

    case MainActor.Messages.BinaryCreated(id) =>
      syncBinary(id)

    case MainActor.Messages.BinarySync(id) =>
      syncBinary(id)

    case MainActor.Messages.BinarySyncCompleted(id) =>
      projectBroadcast(ProjectActor.Messages.BinarySynced(id))

    case MainActor.Messages.BinaryDeleted(id) =>
      binaryActors.remove(id).foreach { ref =>
        ref ! BinaryActor.Messages.Deleted
      }

    case MainActor.Messages.BinaryVersionCreated(_, binaryId) =>
      syncBinaryVersion(binaryId)

    case MainActor.Messages.BinaryVersionDeleted(_, binaryId) =>
      syncBinaryVersion(binaryId)

    case MainActor.Messages.ResolverCreated(id) =>
      upsertResolverActor(id) ! ResolverActor.Messages.Sync

    case MainActor.Messages.ResolverDeleted(id) =>
      resolverActors.remove(id).foreach { ref =>
        ref ! ResolverActor.Messages.Deleted
      }

    case MainActor.Messages.SyncAll =>
      Pager.create { offset =>
        binariesDao.findAll(offset = offset, limit = 1000)
      }.foreach { rec =>
        self ! MainActor.Messages.BinarySync(rec.id)
      }

      Pager.create { offset =>
        librariesDao.findAll(Authorization.All, offset = offset, limit = 1000)
      }.foreach { rec =>
        self ! MainActor.Messages.LibrarySync(rec.id)
      }

      Pager.create { offset =>
        projectsDao.findAll(Authorization.All, offset = offset, limit = 1000)
      }.foreach { rec =>
        self ! MainActor.Messages.ProjectSync(rec.id)
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

  def upsertLibraryActor(id: String): ActorRef = {
    libraryActors.lift(id).getOrElse {
      val ref = system.actorOf(Props(new LibraryActor(
        librariesDao,
        syncsDao,
        resolversDao,
        libraryVersionsDao,
        itemsDao,
        projectLibrariesDao,
        usersDao,
        logger
      )), name = s"$name:libraryActor:$id")
      ref ! LibraryActor.Messages.Data(id)
      libraryActors += (id -> ref)
      ref
    }
  }

  def upsertBinaryActor(id: String): ActorRef = {
    binaryActors.lift(id).getOrElse {
      val ref = system.actorOf(Props(new BinaryActor(
        binariesDao,
        syncsDao,
        binaryVersionsDao,
        usersDao,
        itemsDao,
        projectBinariesDao,
        defaultBinaryVersionProvider,
        logger
      )), name = s"$name:binaryActor:$id")
      ref ! BinaryActor.Messages.Data(id)
      binaryActors += (id -> ref)
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

  def syncLibrary(id: String): Unit = {
    upsertLibraryActor(id) ! LibraryActor.Messages.Sync
    searchActor ! SearchActor.Messages.SyncLibrary(id)
    projectBroadcast(ProjectActor.Messages.LibrarySynced(id))
  }


  def syncLibraryVersion(libraryId: String): Unit = {
    syncLibrary(libraryId)
  }

  def syncBinary(id: String): Unit = {
    upsertBinaryActor(id) ! BinaryActor.Messages.Sync
    searchActor ! SearchActor.Messages.SyncBinary(id)
    projectBroadcast(ProjectActor.Messages.BinarySynced(id))
  }

  def syncBinaryVersion(binaryId: String): Unit = {
    syncBinary(binaryId)
  }

  def projectBroadcast(message: ProjectActor.Message): Unit = {
    projectActors.foreach { case (_, actor) =>
      actor ! message
    }
  }

}
