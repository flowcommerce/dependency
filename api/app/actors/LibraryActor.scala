package io.flow.dependency.actors

import db.{Authorization, InternalItemsDao, InternalProjectLibrariesDao, StaticUserProvider}
import io.flow.akka.SafeReceive
import io.flow.akka.actor.ReapedActor
import io.flow.log.RollbarLogger

import javax.inject.Inject

object LibraryActor {

  object Messages {
    case class Delete(id: String)
  }

}

class LibraryActor @Inject() (
  itemsDao: InternalItemsDao,
  projectLibrariesDao: InternalProjectLibrariesDao,
  staticUserProvider: StaticUserProvider,
  logger: RollbarLogger,
  @javax.inject.Named("project-actor") projectActor: akka.actor.ActorRef,
) extends ReapedActor {

  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)

  def receive: Receive = SafeReceive.withLogUnhandled {

    case LibraryActor.Messages.Delete(libraryId: String) => {
      itemsDao.deleteByObjectId(staticUserProvider.systemUser, libraryId)

      projectLibrariesDao
        .findAll(Authorization.All, libraryId = Some(libraryId), limit = None, orderBy = None)
        .foreach { projectLibrary =>
          projectLibrariesDao.removeLibrary(staticUserProvider.systemUser, projectLibrary)
          projectActor ! ProjectActor.Messages.ProjectLibrarySync(projectLibrary.projectId, projectLibrary.id)
        }
    }
  }

}
