package lib

import javax.inject.{Inject, Singleton}

import db.{Authorization, LibrariesDao}
import io.flow.common.v0.models.UserReference
import io.flow.dependency.actors.MainActor
import io.flow.dependency.v0.models.Library

@Singleton
class SyncsService @Inject()(librariesDao: LibrariesDao,
                             @javax.inject.Named("main-actor") mainActor: akka.actor.ActorRef) {

  def syncLibrary(id: String, user: UserReference): Option[Unit] = {
    val library = findLibrary(id, user)

    library.map { _ =>
      mainActor ! MainActor.Messages.LibrarySync(id)
    }
  }

  private def findLibrary(id: String, user: UserReference): Option[Library] = {
    librariesDao.findById(Authorization.User(user.id), id)
  }
}
