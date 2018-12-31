package io.flow.dependency.actors

import javax.inject.Inject
import io.flow.dependency.v0.models.Binary
import io.flow.dependency.api.lib.DefaultBinaryVersionProvider
import io.flow.postgresql.Pager
import db.{Authorization, BinariesDao, BinaryVersionsDao, ItemsDao, ProjectBinariesDao, SyncsDao, UsersDao}
import akka.actor.Actor
import io.flow.akka.SafeReceive
import io.flow.log.RollbarLogger

object BinaryActor {

  object Messages {
    case class Data(id: String)
    case object Sync
    case object Deleted
  }

}

class BinaryActor @Inject() (
  binariesDao: BinariesDao,
  syncsDao: SyncsDao,
  binaryVersionsDao: BinaryVersionsDao,
  usersDao: UsersDao,
  itemsDao: ItemsDao,
  projectBinariesDao: ProjectBinariesDao,
  defaultBinaryVersionProvider: DefaultBinaryVersionProvider,
  logger: RollbarLogger
) extends Actor {

  var dataBinary: Option[Binary] = None
  lazy val SystemUser = usersDao.systemUser
  private[this] implicit val configuredRollbar = logger.fingerprint("BinaryActor")

  def receive = SafeReceive.withLogUnhandled {

    case BinaryActor.Messages.Data(id: String) =>
      dataBinary = binariesDao.findById(id)

    case BinaryActor.Messages.Sync =>
      dataBinary.foreach { binary =>
        syncsDao.withStartedAndCompleted(SystemUser, "binary", binary.id) {
          defaultBinaryVersionProvider.versions(binary.name).foreach { version =>
            binaryVersionsDao.upsert(usersDao.systemUser, binary.id, version.value)
          }
        }

        sender ! MainActor.Messages.BinarySyncCompleted(binary.id)
      }

    case BinaryActor.Messages.Deleted =>
      dataBinary.foreach { binary =>
        itemsDao.deleteByObjectId(Authorization.All, SystemUser, binary.id)

        Pager.create { offset =>
          projectBinariesDao.findAll(Authorization.All, binaryId = Some(binary.id), offset = offset)
        }.foreach { projectBinary =>
          projectBinariesDao.removeBinary(SystemUser, projectBinary)
          sender ! MainActor.Messages.ProjectBinarySync(projectBinary.project.id, projectBinary.id)
        }
      }
      context.stop(self)
  }

}
