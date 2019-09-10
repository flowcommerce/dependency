package actors

import akka.actor.{Actor, ActorSystem}
import db.{InternalTasksDao, UsersDao}
import io.flow.akka.SafeReceive
import io.flow.log.RollbarLogger
import javax.inject.Inject
import lib.TasksUtil
import sync.{BinarySync, LibrarySync, ProjectSync}

import scala.concurrent.ExecutionContext

object TaskExecutorActor {
  object Messages {
    case class SyncAll(taskId: String)
    case class SyncBinary(taskId: String, binaryId: String)
    case class SyncLibrary(taskId: String, libraryId: String)
    case class SyncProject(taskId: String, projectId: String)
  }
}

class TaskExecutorActor @Inject() (
  system: ActorSystem,
  rollbar: RollbarLogger,
  binarySync: BinarySync,
  librarySync: LibrarySync,
  projectSync: ProjectSync,
  internalTasksDao: InternalTasksDao,
  tasksUtil: TasksUtil,
  usersDao: UsersDao,
) extends Actor {

  private[this] implicit val logger: RollbarLogger = rollbar.fingerprint(getClass.getName)
  private[this] implicit val ec: ExecutionContext = system.dispatchers.lookup("main-actor-context")

  def receive: Receive = SafeReceive.withLogUnhandled {

    case TaskExecutorActor.Messages.SyncBinary(taskId: String, binaryId: String) => {
      tasksUtil.process(taskId) {
        binarySync.sync(usersDao.systemUser, binaryId)
      }
    }

    case TaskExecutorActor.Messages.SyncLibrary(taskId: String, libraryId: String) => {
      tasksUtil.process(taskId) {
        librarySync.sync(usersDao.systemUser, libraryId)
      }
    }

    case TaskExecutorActor.Messages.SyncProject(taskId: String, projectId: String) => {
      tasksUtil.process(taskId) {
        projectSync.sync(usersDao.systemUser, projectId)
      }
    }

    case TaskExecutorActor.Messages.SyncAll(taskId) => {
      tasksUtil.process(taskId) {
        binarySync.forall(internalTasksDao.createSyncIfNotQueued)
        librarySync.forall(internalTasksDao.createSyncIfNotQueued)
        projectSync.forall(internalTasksDao.createSyncIfNotQueued)
      }
    }

  }
}
