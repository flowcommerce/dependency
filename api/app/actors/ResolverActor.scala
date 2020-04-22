package io.flow.dependency.actors

import javax.inject.Inject
import io.flow.dependency.v0.models.Visibility
import io.flow.postgresql.Pager
import db._
import akka.actor.Actor
import cache.ResolversCache
import io.flow.akka.SafeReceive
import io.flow.log.RollbarLogger

object ResolverActor {

  trait Message

  object Messages {
    case class Created(resolverId: String) extends Message
    case class Sync(resolverId: String) extends Message
    case class Deleted(resolverId: String) extends Message
  }

}

class ResolverActor @Inject()(
  resolversCache: ResolversCache,
  librariesDao: LibrariesDao,
  projectLibrariesDao: InternalProjectLibrariesDao,
  staticUserProvider: StaticUserProvider,
  rollbar: RollbarLogger,
  @javax.inject.Named("project-actor") projectActor: akka.actor.ActorRef,
) extends Actor {

  private[this] implicit val logger: RollbarLogger = rollbar.fingerprint(getClass.getName)
  private[this] lazy val SystemUser = staticUserProvider.systemUser

  def receive: Receive = SafeReceive.withLogUnhandled {

    case ResolverActor.Messages.Created(resolverId) => sync(resolverId)

    case ResolverActor.Messages.Sync(resolverId) => sync(resolverId)

    case ResolverActor.Messages.Deleted(resolverId) =>
      Pager.create { offset =>
        librariesDao.findAll(Authorization.All, resolverId = Some(resolverId), limit = None, offset = offset)
      }.foreach { library =>
        librariesDao.delete(SystemUser, library)
      }
  }

  def sync(resolverId: String): Unit = {
    resolversCache.findByResolverId(resolverId).foreach { resolver =>
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

      projectLibrariesDao.findAll(auth, hasLibrary = Some(false), limit = None, orderBy = None).foreach { projectLibrary =>
        projectActor ! ProjectActor.Messages.ProjectLibrarySync(projectLibrary.projectId, projectLibrary.id)
      }
    }
  }

}
