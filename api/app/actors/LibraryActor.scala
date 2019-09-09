package io.flow.dependency.actors

import javax.inject.Inject
import io.flow.postgresql.Pager
import db.{Authorization, ItemsDao, ProjectLibrariesDao, UsersDao}
import akka.actor.Actor
import io.flow.akka.SafeReceive
import io.flow.log.RollbarLogger

object LibraryActor {

  object Messages {
    case class Delete(id: String)
  }

}

class LibraryActor @Inject()(
  itemsDao: ItemsDao,
  projectLibrariesDao: ProjectLibrariesDao,
  usersDao: UsersDao,
  logger: RollbarLogger
) extends Actor {

  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)

  def receive: Receive = SafeReceive.withLogUnhandled {

    case LibraryActor.Messages.Delete(libraryId: String) => {
      itemsDao.deleteByObjectId(Authorization.All, usersDao.systemUser, libraryId)

      Pager.create { offset =>
        projectLibrariesDao.findAll(Authorization.All, libraryId = Some(libraryId), limit = Some(100), offset = offset)
      }.foreach { projectLibrary =>
        projectLibrariesDao.removeLibrary(usersDao.systemUser, projectLibrary)
        sender ! MainActor.Messages.ProjectLibrarySync(projectLibrary.project.id, projectLibrary.id)
      }

      context.stop(self)
    }
  }

}
