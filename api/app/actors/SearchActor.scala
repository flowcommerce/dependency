package io.flow.dependency.actors

import javax.inject.Inject
import db._
import akka.actor.Actor
import io.flow.akka.SafeReceive
import io.flow.common.v0.models.UserReference
import io.flow.log.RollbarLogger

object SearchActor {

  sealed trait Message

  object Messages {
    case class SyncBinary(id: String) extends Message
    case class SyncLibrary(id: String) extends Message
    case class SyncProject(id: String) extends Message
  }

}

class SearchActor @Inject()(
  binariesDao: BinariesDao,
  librariesDao: LibrariesDao,
  projectsDao: ProjectsDao,
  itemsDao: ItemsDao,
  usersDao: UsersDao,
  logger: RollbarLogger
) extends Actor {

  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)

  private[this] lazy val SystemUser: UserReference = usersDao.systemUser

  def receive: Receive = SafeReceive.withLogUnhandled {

    case SearchActor.Messages.SyncBinary(id) =>
      binariesDao.findById(id) match {
        case None => itemsDao.deleteByObjectId(Authorization.All, SystemUser, id)
        case Some(binary) => itemsDao.replaceBinary(SystemUser, binary)
      }
      ()

    case SearchActor.Messages.SyncLibrary(id) =>
      librariesDao.findById(Authorization.All, id) match {
        case None => itemsDao.deleteByObjectId(Authorization.All, SystemUser, id)
        case Some(library) => itemsDao.replaceLibrary(SystemUser, library)
      }
      ()

    case SearchActor.Messages.SyncProject(id) =>
      projectsDao.findById(Authorization.All, id) match {
        case None => itemsDao.deleteByObjectId(Authorization.All, SystemUser, id)
        case Some(project) => itemsDao.replaceProject(SystemUser, project)
      }
      ()
  }

}
