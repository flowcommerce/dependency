package io.flow.dependency.actors

import javax.inject.Inject
import db.{Authorization, InternalItemsDao, InternalProjectLibrariesDao, StaticUserProvider}
import akka.actor.Actor
import io.flow.akka.SafeReceive
import io.flow.log.RollbarLogger

object LibraryActor {

  object Messages {
    case class Delete(id: String)
  }

}

class LibraryActor @Inject()(
  itemsDao: InternalItemsDao,
  projectLibrariesDao: InternalProjectLibrariesDao,
  staticUserProvider: StaticUserProvider,
  logger: RollbarLogger,
  @javax.inject.Named("project-actor") projectActor: akka.actor.ActorRef,
) extends Actor {

  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)

  def receive: Receive = SafeReceive.withLogUnhandled {

    case LibraryActor.Messages.Delete(libraryId: String) => {
      itemsDao.deleteByObjectId(staticUserProvider.systemUser, libraryId)

      projectLibrariesDao.findAll(Authorization.All, libraryId = Some(libraryId), limit = None, orderBy = None).foreach { projectLibrary =>
        projectLibrariesDao.removeLibrary(staticUserProvider.systemUser, projectLibrary)
        projectActor ! ProjectActor.Messages.ProjectLibrarySync(projectLibrary.projectId, projectLibrary.id)
      }
    }
  }

}
