package actors

import akka.actor.{Actor, ActorLogging, ActorSystem}
import db.InternalTasksDao
import io.flow.akka.SafeReceive
import io.flow.akka.recurring.{ScheduleConfig, Scheduler}
import io.flow.dependency.v0.models.TaskDataSync
import io.flow.log.RollbarLogger
import io.flow.play.util.ApplicationConfig
import javax.inject.Inject
import lib.TasksUtil

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object TaskSyncActor {
  case class ProcessTaskMessage(data: TaskDataSync)
}

class TaskSyncActor @Inject()(
  system: ActorSystem,
  internalTasksDao: InternalTasksDao,
  config: ApplicationConfig,
  logger: RollbarLogger
) extends Actor {

  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)
  private[this] implicit val ec: ExecutionContext = system.dispatchers.lookup("task-syncs-actor-context")

  def receive = SafeReceive.withLogUnhandled {
    case TaskSyncActor.ProcessTaskMessage(data) => {
      println(s"got data")
    }
  }

}
