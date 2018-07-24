package io.flow.dependency.actors

import actors.UpgradeActor
import db._
import io.flow.play.util.Config
import io.flow.play.actors.{ErrorHandler, Scheduler}
import play.api.libs.concurrent.Akka
import akka.actor._
import io.flow.postgresql.Pager
import lib.UpgradeService
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.InjectedActorSupport

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

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
  projectFactory: ProjectActor.Factory,
  override val config: io.flow.play.util.Config,
  system: ActorSystem,
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
  upgradeService: UpgradeService
) extends Actor with ActorLogging with ErrorHandler with Scheduler with InjectedActorSupport {

  import scala.concurrent.duration._

  private[this] val name = "main"

  private[this] val emailActor = system.actorOf(Props(new EmailActor(
    subscriptionsDao,
    batchEmailProcessor,
    config
  )), name = s"$name:emailActor")
  private[this] val periodicActor = system.actorOf(Props(new PeriodicActor(
    syncsDao,
    projectsDao,
    binariesDao,
    librariesDao
  )), name = s"$name:periodicActor")
  private[this] val searchActor = system.actorOf(Props(new SearchActor(
    binariesDao: BinariesDao,
    librariesDao,
    projectsDao,
    itemsDao,
    usersDao
  )), name = s"$name:SearchActor")

  private[this] val binaryActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val libraryActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val projectActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val userActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val resolverActors = scala.collection.mutable.Map[String, ActorRef]()
  private[this] val upgradeActor = system.actorOf(UpgradeActor.props(upgradeService), UpgradeActor.Name)

  implicit val mainActorExecutionContext: ExecutionContext = system.dispatchers.lookup("main-actor-context")

  scheduleRecurring(system, "io.flow.dependency.api.binary.seconds") {
    periodicActor ! PeriodicActor.Messages.SyncBinaries
  }

  scheduleRecurring(system, "io.flow.dependency.api.library.seconds") {
    periodicActor !  PeriodicActor.Messages.SyncLibraries
  }

  scheduleRecurring(system, "io.flow.dependency.api.library.upgrade.seconds") {
    periodicActor !  PeriodicActor.Messages.UpgradeLibraries
  }

  scheduleRecurring(system, "io.flow.dependency.api.project.seconds") {
    periodicActor !  PeriodicActor.Messages.SyncProjects
  }

  scheduleRecurring(system, "io.flow.dependency.api.purge.seconds") {
    periodicActor !  PeriodicActor.Messages.Purge
  }

  scheduleRecurring(system, "io.flow.dependency.api.email.seconds") {
    emailActor ! EmailActor.Messages.ProcessDailySummary
  }

  def receive = akka.event.LoggingReceive {

    case m @ MainActor.Messages.UserCreated(id) => withErrorHandler(m) {
      upsertUserActor(id) ! UserActor.Messages.Created
    }

    case m @ MainActor.Messages.ProjectCreated(id) => withErrorHandler(m) {
      val actor = upsertProjectActor(id)
      actor ! ProjectActor.Messages.CreateHooks
      actor ! ProjectActor.Messages.Sync
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case m @ MainActor.Messages.ProjectUpdated(id) => withErrorHandler(m) {
      upsertProjectActor(id) ! ProjectActor.Messages.Sync
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case m @ MainActor.Messages.ProjectDeleted(id) => withErrorHandler(m) {
      projectActors.remove(id).map { actor =>
        actor ! ProjectActor.Messages.Deleted
      }
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case m @ MainActor.Messages.ProjectSync(id) => withErrorHandler(m) {
      upsertProjectActor(id) ! ProjectActor.Messages.Sync
      searchActor ! SearchActor.Messages.SyncProject(id)
    }

    case m @ MainActor.Messages.ProjectLibraryCreated(projectId, id) => withErrorHandler(m) {
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectLibraryCreated(id)
    }

    case m @ MainActor.Messages.ProjectLibrarySync(projectId, id) => withErrorHandler(m) {
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectLibrarySync(id)
    }

    case m @ MainActor.Messages.ProjectLibraryDeleted(projectId, id, version) => withErrorHandler(m) {
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectLibraryDeleted(id, version)
    }

    case m @ MainActor.Messages.ProjectBinaryCreated(projectId, id) => withErrorHandler(m) {
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectBinaryCreated(id)
    }

    case m @ MainActor.Messages.ProjectBinarySync(projectId, id) => withErrorHandler(m) {
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectBinarySync(id)
    }

    case m @ MainActor.Messages.ProjectBinaryDeleted(projectId, id, version) => withErrorHandler(m) {
      upsertProjectActor(projectId) ! ProjectActor.Messages.ProjectBinaryDeleted(id, version)
    }

    case m @ MainActor.Messages.LibraryCreated(id) => withErrorHandler(m) {
      syncLibrary(id)
    }

    case m @ MainActor.Messages.LibrarySync(id) => withErrorHandler(m) {
      syncLibrary(id)
    }

    case m @ MainActor.Messages.LibrarySyncFuture(id, seconds) => withErrorHandler(m) {
      system.scheduler.scheduleOnce(Duration(seconds, "seconds")) {
        syncLibrary(id)
      }
    }

    case m @ MainActor.Messages.LibrarySyncCompleted(id) => withErrorHandler(m) {
      projectBroadcast(ProjectActor.Messages.LibrarySynced(id))
    }

    case m @ MainActor.Messages.LibraryDeleted(id) => withErrorHandler(m) {
      libraryActors.remove(id).map { ref =>
        ref ! LibraryActor.Messages.Deleted
      }
    }

    case m @ MainActor.Messages.LibraryVersionCreated(id, libraryId) => withErrorHandler(m) {
      syncLibraryVersion(id, libraryId)
    }

    case m @ MainActor.Messages.LibraryVersionDeleted(id, libraryId) => withErrorHandler(m) {
      syncLibraryVersion(id, libraryId)
    }

    case m @ MainActor.Messages.BinaryCreated(id) => withErrorHandler(m) {
      syncBinary(id)
    }

    case m @ MainActor.Messages.BinarySync(id) => withErrorHandler(m) {
      syncBinary(id)
    }

    case m @ MainActor.Messages.BinarySyncCompleted(id) => withErrorHandler(m) {
      projectBroadcast(ProjectActor.Messages.BinarySynced(id))
    }

    case m @ MainActor.Messages.BinaryDeleted(id) => withErrorHandler(m) {
      binaryActors.remove(id).map { ref =>
        ref ! BinaryActor.Messages.Deleted
      }
    }

    case m @ MainActor.Messages.BinaryVersionCreated(id, binaryId) => withErrorHandler(m) {
      syncBinaryVersion(id, binaryId)
    }

    case m @ MainActor.Messages.BinaryVersionDeleted(id, binaryId) => withErrorHandler(m) {
      syncBinaryVersion(id, binaryId)
    }

    case m @ MainActor.Messages.ResolverCreated(id) => withErrorHandler(m) {
      upsertResolverActor(id) ! ResolverActor.Messages.Sync
    }

    case m @ MainActor.Messages.ResolverDeleted(id) => withErrorHandler(m) {
      resolverActors.remove(id).map { ref =>
        ref ! ResolverActor.Messages.Deleted
      }
    }

    case m @ MainActor.Messages.SyncAll => withErrorHandler(m) {
      Pager.create { offset =>
        binariesDao.findAll(Authorization.All, offset = offset, limit = 1000)
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

    case m: Any => logUnhandledMessage(m)

  }

  def upsertUserActor(id: String): ActorRef = {
    userActors.lift(id).getOrElse {
      val ref = system.actorOf(Props(new UserActor(
        organizationsDao,
        userIdentifiersDao,
        subscriptionsDao,
        usersDao
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
        usersDao
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
        projectBinariesDao
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
        usersDao
      )), name = s"$name:resolverActor:$id")
      ref ! ResolverActor.Messages.Data(id)
      resolverActors += (id -> ref)
      ref
    }
  }

  def syncLibrary(id: String) {
    upsertLibraryActor(id) ! LibraryActor.Messages.Sync
    searchActor ! SearchActor.Messages.SyncLibrary(id)
    projectBroadcast(ProjectActor.Messages.LibrarySynced(id))
  }


  def syncLibraryVersion(id: String, libraryId: String) {
    syncLibrary(libraryId)
  }

  def syncBinary(id: String) {
    upsertBinaryActor(id) ! BinaryActor.Messages.Sync
    searchActor ! SearchActor.Messages.SyncBinary(id)
    projectBroadcast(ProjectActor.Messages.BinarySynced(id))
  }

  def syncBinaryVersion(id: String, binaryId: String) {
    syncBinary(binaryId)
  }

  def projectBroadcast(message: ProjectActor.Message) {
    projectActors.foreach { case (projectId, actor) =>
      actor ! message
    }
  }

}
