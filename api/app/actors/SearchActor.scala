package io.flow.dependency.actors

import db._
import io.flow.akka.SafeReceive
import io.flow.akka.actor.ReapedActor
import io.flow.common.v0.models.UserReference
import io.flow.log.RollbarLogger

import javax.inject.Inject

object SearchActor {

  sealed trait Message

  object Messages {
    case class SyncBinary(id: String) extends Message
    case class SyncLibrary(id: String) extends Message
    case class SyncProject(id: String) extends Message
  }

}

class SearchActor @Inject() (
  binariesDao: BinariesDao,
  librariesDao: LibrariesDao,
  projectsDao: ProjectsDao,
  internalItemsDao: InternalItemsDao,
  staticUserProvider: StaticUserProvider,
  logger: RollbarLogger,
) extends ReapedActor {

  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)

  private[this] lazy val SystemUser: UserReference = staticUserProvider.systemUser

  def receive: Receive = SafeReceive.withLogUnhandled {

    case SearchActor.Messages.SyncBinary(id) =>
      binariesDao.findById(id) match {
        case None => internalItemsDao.deleteByObjectId(SystemUser, id)
        case Some(binary) => internalItemsDao.replaceBinary(SystemUser, binary)
      }
      ()

    case SearchActor.Messages.SyncLibrary(id) =>
      librariesDao.findById(Authorization.All, id) match {
        case None => internalItemsDao.deleteByObjectId(SystemUser, id)
        case Some(library) => internalItemsDao.replaceLibrary(SystemUser, library)
      }
      ()

    case SearchActor.Messages.SyncProject(id) =>
      projectsDao.findById(Authorization.All, id) match {
        case None => internalItemsDao.deleteByObjectId(SystemUser, id)
        case Some(project) => internalItemsDao.replaceProject(SystemUser, project)
      }
      ()
  }

}
