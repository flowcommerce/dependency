package io.flow.dependency.actors

import javax.inject.Inject

import io.flow.dependency.v0.models.{BinarySummary, LibrarySummary, ProjectSummary}
import db._
import play.api.Logger
import akka.actor.{Actor, ActorSystem}

import scala.concurrent.ExecutionContext

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
  usersDao: UsersDao
) extends Actor with Util {


  lazy val SystemUser = usersDao.systemUser

  def receive = {


    case m @ SearchActor.Messages.SyncBinary(id) => withErrorHandler(m) {
      println(s"SearchActor.Messages.SyncBinary($id)")
      binariesDao.findById(Authorization.All, id) match {
        case None => itemsDao.deleteByObjectId(Authorization.All, SystemUser, id)
        case Some(binary) => itemsDao.replaceBinary(SystemUser, binary)
      }
    }

    case m @ SearchActor.Messages.SyncLibrary(id) => withErrorHandler(m) {
      librariesDao.findById(Authorization.All, id) match {
        case None => itemsDao.deleteByObjectId(Authorization.All, SystemUser, id)
        case Some(library) => itemsDao.replaceLibrary(SystemUser, library)
      }
    }

    case m @ SearchActor.Messages.SyncProject(id) => withErrorHandler(m) {
      projectsDao.findById(Authorization.All, id) match {
        case None => itemsDao.deleteByObjectId(Authorization.All, SystemUser, id)
        case Some(project) => itemsDao.replaceProject(SystemUser, project)
      }
    }

    case m: Any => logUnhandledMessage(m)
  }

}
