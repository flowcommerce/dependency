package io.flow.dependency.actors

import javax.inject.Inject

import io.flow.dependency.v0.models.{BinarySummary, LibrarySummary, ProjectSummary}
import db._
import play.api.Logger
import akka.actor.Actor

object SearchActor {

  sealed trait Message

  object Messages {
    case class SyncBinary(id: String) extends Message
    case class SyncLibrary(id: String) extends Message
    case class SyncProject(id: String) extends Message
  }

}

class SearchActor @Inject()(
  BinariesDao: BinariesDao,
  LibrariesDao: LibrariesDao,
  ProjectsDao: ProjectsDao,
  ItemsDao: ItemsDao,
  usersDao: UsersDao
) extends Actor with Util {

  lazy val SystemUser = usersDao.systemUser
  
  def receive = {

    
    case m @ SearchActor.Messages.SyncBinary(id) => withErrorHandler(m) {
      println(s"SearchActor.Messages.SyncBinary($id)")
      BinariesDao.findById(Authorization.All, id) match {
        case None => ItemsDao.deleteByObjectId(Authorization.All, SystemUser, id)
        case Some(binary) => ItemsDao.replaceBinary(SystemUser, binary)
      }
    }

    case m @ SearchActor.Messages.SyncLibrary(id) => withErrorHandler(m) {
      LibrariesDao.findById(Authorization.All, id) match {
        case None => ItemsDao.deleteByObjectId(Authorization.All, SystemUser, id)
        case Some(library) => ItemsDao.replaceLibrary(SystemUser, library)
      }
    }

    case m @ SearchActor.Messages.SyncProject(id) => withErrorHandler(m) {
      ProjectsDao.findById(Authorization.All, id) match {
        case None => ItemsDao.deleteByObjectId(Authorization.All, SystemUser, id)
        case Some(project) => ItemsDao.replaceProject(SystemUser, project)
      }
    }

    case m: Any => logUnhandledMessage(m)
  }

}
