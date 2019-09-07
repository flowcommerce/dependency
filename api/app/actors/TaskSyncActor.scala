package actors

import akka.actor.Actor
import db.InternalTasksDao
import io.flow.akka.SafeReceive
import io.flow.dependency.v0.models.TaskDataSync
import io.flow.log.RollbarLogger
import javax.inject.Inject

object TaskSyncActor {
  case class ProcessTaskMessage(taskId: String, data: TaskDataSync)
}

class TaskSyncActor @Inject()(
  internalTasksDao: InternalTasksDao,
  logger: RollbarLogger
) extends Actor {

  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)

  def receive = SafeReceive.withLogUnhandled {
    case TaskSyncActor.ProcessTaskMessage(taskId, data) => {
      println(s"got data: $data")
      internalTasksDao.setProcessed(taskId)
    }
  }

}
