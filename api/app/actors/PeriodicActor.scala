package io.flow.dependency.actors

import io.flow.postgresql.Pager
import db.{Authorization, BinariesDao, LibrariesDao, ProjectsDao, SyncsDao}
import play.api.Logger
import akka.actor.Actor

object PeriodicActor {

  sealed trait Message

  object Messages {
    case object Purge extends Message
    case object SyncBinaries extends Message
    case object SyncLibraries extends Message
    case object SyncProjects extends Message
  }

}

class PeriodicActor extends Actor with Util {

  def receive = {

    case m @ PeriodicActor.Messages.Purge => withErrorHandler(m) {
      SyncsDao.purgeOld()
    }

    case m @ PeriodicActor.Messages.SyncProjects => withErrorHandler(m) {
      Pager.create { offset =>
        ProjectsDao.findAll(Authorization.All, offset = offset)
      }.foreach { project =>
        sender ! MainActor.Messages.ProjectSync(project.id)
      }
    }

    case m @ PeriodicActor.Messages.SyncBinaries => withErrorHandler(m) {
      Pager.create { offset =>
        BinariesDao.findAll(Authorization.All, offset = offset)
      }.foreach { bin =>
        sender ! MainActor.Messages.BinarySync(bin.id)
      }
    }

    case m @ PeriodicActor.Messages.SyncLibraries => withErrorHandler(m) {
      Pager.create { offset =>
        LibrariesDao.findAll(Authorization.All, offset = offset)
      }.foreach { library =>
        sender ! MainActor.Messages.LibrarySync(library.id)
      }
    }

    case m: Any => logUnhandledMessage(m)
  }

}
