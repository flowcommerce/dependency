package io.flow.dependency.actors

import javax.inject.Inject

import io.flow.postgresql.Pager
import db.{Authorization, BinariesDao, LibrariesDao, ProjectsDao, SyncsDao}

import akka.actor.{Actor, ActorSystem}

import scala.concurrent.ExecutionContext

object PeriodicActor {

  sealed trait Message

  object Messages {
    case object Purge extends Message
    case object SyncBinaries extends Message
    case object SyncLibraries extends Message
    case object SyncProjects extends Message
  }

}

class PeriodicActor @Inject()(
  syncsDao: SyncsDao,
  projectsDao: ProjectsDao,
  binariesDao: BinariesDao,
  librariesDao: LibrariesDao
) extends Actor with Util {


  def receive = {

    case m @ PeriodicActor.Messages.Purge => withErrorHandler(m) {
      syncsDao.purgeOld()
    }

    case m @ PeriodicActor.Messages.SyncProjects => withErrorHandler(m) {
      Pager.create { offset =>
        projectsDao.findAll(Authorization.All, offset = offset)
      }.foreach { project =>
        sender ! MainActor.Messages.ProjectSync(project.id)
      }
    }

    case m @ PeriodicActor.Messages.SyncBinaries => withErrorHandler(m) {
      Pager.create { offset =>
        binariesDao.findAll(Authorization.All, offset = offset)
      }.foreach { bin =>
        sender ! MainActor.Messages.BinarySync(bin.id)
      }
    }

    case m @ PeriodicActor.Messages.SyncLibraries => withErrorHandler(m) {
      Pager.create { offset =>
        librariesDao.findAll(Authorization.All, offset = offset)
      }.foreach { library =>
        sender ! MainActor.Messages.LibrarySync(library.id)
      }
    }

    case m: Any => logUnhandledMessage(m)
  }

}
