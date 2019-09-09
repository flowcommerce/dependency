package actors

import akka.actor.Actor
import db.{InternalTasksDao, LibrariesDao, ProjectsDao, UsersDao}
import io.flow.akka.SafeReceive
import io.flow.log.RollbarLogger
import javax.inject.Inject
import lib.TaskUtil
import sync.{BinarySync, LibrarySync, ProjectSync}

object TaskExecutorActor {
  object Messages {
    case class SyncAll(taskId: String)
    case class SyncBinary(taskId: String, binaryId: String)
    case class SyncLibrary(taskId: String, libraryId: String)
    case class SyncProject(taskId: String, projectId: String)
  }
}

class TaskExecutorActor @Inject() (
  rollbar: RollbarLogger,
  binarySync: BinarySync,
  librarySync: LibrarySync,
  projectSync: ProjectSync,
  internalTasksDao: InternalTasksDao,
  taskUtil: TaskUtil,
  usersDao: UsersDao,
) extends Actor {

  private[this] implicit val logger: RollbarLogger = rollbar.fingerprint(getClass.getName)

  def receive: Receive = SafeReceive.withLogUnhandled {

    case TaskExecutorActor.Messages.SyncBinary(taskId: String, binaryId: String) => {
      taskUtil.process(taskId) {
        binarySync.sync(usersDao.systemUser, binaryId)
      }
    }

    case TaskExecutorActor.Messages.SyncLibrary(taskId: String, libraryId: String) => {
      taskUtil.process(taskId) {
        librarySync.sync(usersDao.systemUser, libraryId)
      }
    }

    case TaskExecutorActor.Messages.SyncProject(taskId: String, projectId: String) => {
      taskUtil.process(taskId) {
        projectSync.sync(usersDao.systemUser, projectId)
      }
    }

    case TaskExecutorActor.Messages.SyncAll(taskId) => {
      taskUtil.process(taskId) {
        binarySync.forall(internalTasksDao.createSyncIfNotQueued)
        librarySync.forall(internalTasksDao.createSyncIfNotQueued)
        projectSync.forall(internalTasksDao.createSyncIfNotQueued)
      }
    }

  }
}
