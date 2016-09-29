package com.bryzek.dependency.actors

import com.bryzek.dependency.v0.models.{BinarySummary, LibrarySummary, ProjectSummary}
import db.{Authorization, BinariesDao, ItemForm, ItemsDao, LibrariesDao, ProjectsDao}
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

class SearchActor extends Actor with Util {

  def receive = {

    case m @ SearchActor.Messages.SyncBinary(id) => withErrorHandler(m) {
      BinariesDao.findById(Authorization.All, id) match {
        case None => ItemsDao.deleteByObjectId(Authorization.All, MainActor.SystemUser, id)
        case Some(binary) => ItemsDao.replaceBinary(MainActor.SystemUser, binary)
      }
    }

    case m @ SearchActor.Messages.SyncLibrary(id) => withErrorHandler(m) {
      LibrariesDao.findById(Authorization.All, id) match {
        case None => ItemsDao.deleteByObjectId(Authorization.All, MainActor.SystemUser, id)
        case Some(library) => ItemsDao.replaceLibrary(MainActor.SystemUser, library)
      }
    }

    case m @ SearchActor.Messages.SyncProject(id) => withErrorHandler(m) {
      ProjectsDao.findById(Authorization.All, id) match {
        case None => ItemsDao.deleteByObjectId(Authorization.All, MainActor.SystemUser, id)
        case Some(project) => ItemsDao.replaceProject(MainActor.SystemUser, project)
      }
    }

    case m: Any => logUnhandledMessage(m)
  }

}
