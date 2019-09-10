package io.flow.dependency.actors

import javax.inject.Inject
import io.flow.postgresql.Pager
import db.{Authorization, InternalItemsDao, ProjectBinariesDao, StaticUserProvider}
import akka.actor.Actor
import io.flow.akka.SafeReceive
import io.flow.log.RollbarLogger

object BinaryActor {

  object Messages {
    case class Delete(binaryId: String)
  }

}

class BinaryActor @Inject() (
  staticUserProvider: StaticUserProvider,
  itemsDao: InternalItemsDao,
  projectBinariesDao: ProjectBinariesDao,
  logger: RollbarLogger,
  @javax.inject.Named("project-actor") projectActor: akka.actor.ActorRef,
) extends Actor {

  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)

  def receive: Receive = SafeReceive.withLogUnhandled {

    case BinaryActor.Messages.Delete(binaryId: String) =>
      itemsDao.deleteByObjectId(staticUserProvider.systemUser, binaryId)

      Pager.create { offset =>
        projectBinariesDao.findAll(Authorization.All, binaryId = Some(binaryId), offset = offset)
      }.foreach { projectBinary =>
        projectBinariesDao.removeBinary(staticUserProvider.systemUser, projectBinary)
        projectActor ! ProjectActor.Messages.ProjectBinarySync(projectBinary.project.id, projectBinary.id)
      }
  }

}
