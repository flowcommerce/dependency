package io.flow.dependency.actors

import javax.inject.Inject
import io.flow.postgresql.Pager
import db.{Authorization, BinariesDao, LibrariesDao, ProjectsDao, SyncsDao}
import akka.actor.Actor
import io.flow.akka.SafeReceive
import io.flow.log.RollbarLogger

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
  librariesDao: LibrariesDao,
  logger: RollbarLogger
) extends Actor {

  private[this] implicit val configuredRollbar = logger.fingerprint("PeriodicActor")

  def receive = SafeReceive.withLogUnhandled {

    case PeriodicActor.Messages.Purge =>
      syncsDao.purgeOld()

    case PeriodicActor.Messages.SyncProjects =>
      Pager.create { offset =>
        projectsDao.findAll(Authorization.All, offset = offset)
      }.foreach { project =>
        sender ! MainActor.Messages.ProjectSync(project.id)
      }

    case PeriodicActor.Messages.SyncBinaries =>
      Pager.create { offset =>
        binariesDao.findAll(offset = offset)
      }.foreach { bin =>
        sender ! MainActor.Messages.BinarySync(bin.id)
      }

    case PeriodicActor.Messages.SyncLibraries =>
      Pager.create { offset =>
        librariesDao.findAll(Authorization.All, offset = offset)
      }.foreach { library =>
        sender ! MainActor.Messages.LibrarySync(library.id)
      }
  }

}
