package com.bryzek.dependency.actors

import com.bryzek.dependency.v0.models.{Binary, BinaryForm}
import com.bryzek.dependency.api.lib.DefaultBinaryVersionProvider
import io.flow.postgresql.Pager
import db.{Authorization, BinariesDao, BinaryVersionsDao, ItemsDao, ProjectBinariesDao, SyncsDao, UsersDao}
import play.api.Logger
import akka.actor.Actor

object BinaryActor {

  object Messages {
    case class Data(id: String)
    case object Sync
    case object Deleted
  }

}

class BinaryActor extends Actor with Util {

  var dataBinary: Option[Binary] = None

  def receive = {

    case m @ BinaryActor.Messages.Data(id: String) => withErrorHandler(m) {
      dataBinary = BinariesDao.findById(Authorization.All, id)
    }

    case m @ BinaryActor.Messages.Sync => withErrorHandler(m) {
      dataBinary.foreach { binary =>
        SyncsDao.withStartedAndCompleted(MainActor.SystemUser, "binary", binary.id) {
          DefaultBinaryVersionProvider.versions(binary.name).foreach { version =>
            BinaryVersionsDao.upsert(UsersDao.systemUser, binary.id, version.value)
          }
        }

        sender ! MainActor.Messages.BinarySyncCompleted(binary.id)
      }
    }

    case m @ BinaryActor.Messages.Deleted => withErrorHandler(m) {
      dataBinary.foreach { binary =>
        ItemsDao.deleteByObjectId(Authorization.All, MainActor.SystemUser, binary.id)

        Pager.create { offset =>
          ProjectBinariesDao.findAll(Authorization.All, binaryId = Some(binary.id), offset = offset)
        }.foreach { projectBinary =>
          ProjectBinariesDao.removeBinary(MainActor.SystemUser, projectBinary)
          sender ! MainActor.Messages.ProjectBinarySync(projectBinary.project.id, projectBinary.id)
        }
      }
      context.stop(self)
    }

    case m: Any => logUnhandledMessage(m)
  }

}
