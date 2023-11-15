package io.flow.dependency.actors

import db.{Authorization, InternalItemsDao, ProjectBinariesDao, StaticUserProvider}
import io.flow.akka.SafeReceive
import io.flow.akka.actor.ReapedActor
import io.flow.log.RollbarLogger
import io.flow.postgresql.Pager

import javax.inject.Inject

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
) extends ReapedActor {

  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)

  def receive: Receive = SafeReceive.withLogUnhandled { case BinaryActor.Messages.Delete(binaryId: String) =>
    itemsDao.deleteByObjectId(staticUserProvider.systemUser, binaryId)

    Pager
      .create { offset =>
        projectBinariesDao.findAll(Authorization.All, binaryId = Some(binaryId), offset = offset)
      }
      .foreach { projectBinary =>
        projectBinariesDao.removeBinary(staticUserProvider.systemUser, projectBinary)
        projectActor ! ProjectActor.Messages.ProjectBinarySync(projectBinary.project.id, projectBinary.id)
      }
  }

}
