package com.bryzek.dependency.actors

import com.bryzek.dependency.v0.models.{Resolver, Visibility}
import io.flow.postgresql.Pager
import db.{Authorization, LibrariesDao, ProjectLibrariesDao, OrganizationsDao, SubscriptionsDao, ResolversDao}
import akka.actor.Actor
import scala.concurrent.ExecutionContext

object ResolverActor {

  trait Message

  object Messages {
    case class Data(id: String) extends Message
    case object Created extends Message
    case object Sync extends Message
    case object Deleted extends Message
  }

}

class ResolverActor extends Actor with Util {

  var dataResolver: Option[Resolver] = None

  def receive = {

    case m @ ResolverActor.Messages.Data(id) => withErrorHandler(m.toString) {
      dataResolver = ResolversDao.findById(Authorization.All, id)
    }

    case m @ ResolverActor.Messages.Created => withErrorHandler(m.toString) {
      sync()
    }

    case m @ ResolverActor.Messages.Sync => withErrorHandler(m.toString) {
      sync()
    }

    case m @ ResolverActor.Messages.Deleted => withErrorHandler(m.toString) {
      dataResolver.foreach { resolver =>
        Pager.create { offset =>
          LibrariesDao.findAll(Authorization.All, resolverId = Some(resolver.id), offset = offset)
        }.foreach { library =>
          LibrariesDao.delete(MainActor.SystemUser, library)
        }
      }

      context.stop(self)
    }

    case m: Any => logUnhandledMessage(m)
  }

  def sync() {
    dataResolver.foreach { resolver =>
      // Trigger resolution for any project libraries that are currently not resolved.
      val auth = (resolver.organization, resolver.visibility) match {
        case (None, _) => Authorization.All
        case (Some(org), Visibility.Public | Visibility.UNDEFINED(_)) => {
          Authorization.All
        }
        case (Some(org), Visibility.Private) => {
          Authorization.Organization(org.id)
        }
      }

      Pager.create { offset =>
        ProjectLibrariesDao.findAll(auth, hasLibrary = Some(false), offset = offset)
      }.foreach { projectLibrary =>
        sender ! MainActor.Messages.ProjectLibrarySync(projectLibrary.project.id, projectLibrary.id)
      }
    }
  }

}
