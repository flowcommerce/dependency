package io.flow.dependency.actors

import javax.inject.Inject
import io.flow.dependency.v0.models.{Resolver, Visibility}
import io.flow.postgresql.Pager
import db._
import akka.actor.Actor
import io.flow.akka.SafeReceive
import io.flow.log.RollbarLogger

object ResolverActor {

  trait Message

  object Messages {
    case class Data(id: String) extends Message
    case object Created extends Message
    case object Sync extends Message
    case object Deleted extends Message
  }

}

class ResolverActor @Inject()(
  resolversDao: ResolversDao,
  librariesDao: LibrariesDao,
  projectLibrariesDao: ProjectLibrariesDao,
  usersDao: UsersDao,
  logger: RollbarLogger
) extends Actor {

  private[this] implicit val configuredRollbar = logger.fingerprint("ResolverActor")

  var dataResolver: Option[Resolver] = None
  lazy val SystemUser = usersDao.systemUser

  def receive = SafeReceive.withLogUnhandled {

    case ResolverActor.Messages.Data(id) =>
      dataResolver = resolversDao.findById(Authorization.All, id)

    case ResolverActor.Messages.Created =>
      sync()

    case ResolverActor.Messages.Sync =>
      sync()

    case ResolverActor.Messages.Deleted =>
      dataResolver.foreach { resolver =>
        Pager.create { offset =>
          librariesDao.findAll(Authorization.All, resolverId = Some(resolver.id), offset = offset)
        }.foreach { library =>
          librariesDao.delete(SystemUser, library)
        }
      }

      context.stop(self)
  }

  def sync(): Unit = {
    dataResolver.foreach { resolver =>
      // Trigger resolution for any project libraries that are currently not resolved.
      val auth = (resolver.organization, resolver.visibility) match {
        case (None, _) => Authorization.All
        case (Some(_), Visibility.Public | Visibility.UNDEFINED(_)) => {
          Authorization.All
        }
        case (Some(org), Visibility.Private) => {
          Authorization.Organization(org.id)
        }
      }

      Pager.create { offset =>
        projectLibrariesDao.findAll(auth, hasLibrary = Some(false), limit = Some(100), offset = offset)
      }.foreach { projectLibrary =>
        sender ! MainActor.Messages.ProjectLibrarySync(projectLibrary.project.id, projectLibrary.id)
      }
    }
  }

}
