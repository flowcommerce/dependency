package actors

import akka.actor.{Actor, ActorLogging, ActorSystem}
import io.flow.akka.SafeReceive
import io.flow.akka.recurring.{ScheduleConfig, Scheduler}
import io.flow.log.RollbarLogger
import io.flow.play.util.ApplicationConfig
import javax.inject.Inject
import lib.TasksUtil

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class TaskActor @Inject()(
  system: ActorSystem,
  tasksUtil: TasksUtil,
  config: ApplicationConfig,
  logger: RollbarLogger
) extends Actor with ActorLogging with Scheduler {

  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)
  private[this] implicit val ec: ExecutionContext = system.dispatchers.lookup("tasks-actor-context")

  private[this] val MaxTasksPerIteration = 10
  private[this] case object Process

  scheduleRecurring(
    ScheduleConfig.fromConfig(config.underlying.underlying, "io.flow.dependency.api.task"),
    Process
  )

  def receive = SafeReceive.withLogUnhandled {
    case Process => {
      Try {
        tasksUtil.process(MaxTasksPerIteration)
      } match {
        case Success(_) => // no-op
        case Failure(ex) => logger.warn("Error processing tasks", ex)
      }
    }
  }

}
