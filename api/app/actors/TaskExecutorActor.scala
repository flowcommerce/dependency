package actors

import akka.actor.Actor
import db.{Authorization, BinariesDao, BinaryVersionsDao, InternalTasksDao, LibrariesDao, ProjectsDao, SyncsDao, UsersDao}
import io.flow.akka.SafeReceive
import io.flow.dependency.api.lib.DefaultBinaryVersionProvider
import io.flow.log.RollbarLogger
import io.flow.postgresql.Pager
import javax.inject.Inject
import lib.TaskUtil

object TaskExecutorActor {
  object Messages {
    case class SyncAll(taskId: String)
    case class SyncBinary(taskId: String, binaryId: String)
    case class SyncLibrary(taskId: String, libraryId: String)
    case class SyncProject(taskId: String, projectId: String)
  }
}

class TaskExecutorActor @Inject() (
  logger: RollbarLogger,
  binaryVersionsDao: BinaryVersionsDao,
  defaultBinaryVersionProvider: DefaultBinaryVersionProvider,
  binariesDao: BinariesDao,
  librariesDao: LibrariesDao,
  projectsDao: ProjectsDao,
  internalTasksDao: InternalTasksDao,
  syncsDao: SyncsDao,
  taskUtil: TaskUtil,
  usersDao: UsersDao
) extends Actor {

  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)

  def receive: Receive = SafeReceive.withLogUnhandled {

    case TaskExecutorActor.Messages.SyncBinary(taskId: String, binaryId: String) => {
      taskUtil.process(taskId) {
        binariesDao.findById(binaryId).foreach { binary =>
          syncsDao.withStartedAndCompleted("binary", binary.id) {
            defaultBinaryVersionProvider.versions(binary.name).foreach { version =>
              binaryVersionsDao.upsert(usersDao.systemUser, binary.id, version.value)
            }
          }
        }
      }
    }

    case TaskExecutorActor.Messages.SyncLibrary(taskId: String, libraryId: String) => {
      println(s"TODO: SyncLibrary $taskId => $libraryId")
    }

    case TaskExecutorActor.Messages.SyncProject(taskId: String, projectId: String) => {
      println(s"TODO: SyncProject $taskId => $projectId")
    }

    case TaskExecutorActor.Messages.SyncAll(taskId) => {
      taskUtil.process(taskId) {
        Pager.create { offset =>
          binariesDao.findAll(offset = offset, limit = 1000)
        }.foreach { rec =>
          internalTasksDao.createSyncIfNotQueued(rec)
        }

        Pager.create { offset =>
          librariesDao.findAll(Authorization.All, offset = offset, limit = 1000)
        }.foreach { rec =>
          internalTasksDao.createSyncIfNotQueued(rec)
        }

        Pager.create { offset =>
          projectsDao.findAll(Authorization.All, offset = offset, limit = 1000)
        }.foreach { rec =>
          internalTasksDao.createSyncIfNotQueued(rec)
        }
      }
    }

  }
}
