package io.flow.dependency.actors

import java.sql.SQLException

import akka.actor.{Actor, ActorLogging, ActorSystem}
import io.flow.akka.SafeReceive
import io.flow.akka.recurring.{ScheduleConfig, Scheduler}
import io.flow.log.RollbarLogger
import io.flow.play.util.ApplicationConfig
import javax.inject.Inject
import lib.TasksUtil
import play.api.{Environment, Mode}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class TaskActor @Inject()(
  env: Environment,
  system: ActorSystem,
  tasksUtil: TasksUtil,
  config: ApplicationConfig,
  logger: RollbarLogger
) extends Actor with ActorLogging with Scheduler {

  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)
  private[this] implicit val ec: ExecutionContext = system.dispatchers.lookup("tasks-actor-context")

  private[this] val MaxTasksPerIteration = 10L

  scheduleRecurring(
    ScheduleConfig.fromConfig(config.underlying.underlying, "io.flow.dependency.api.task"),
    ReactiveActor.Messages.Changed
  )

  def receive: Receive = SafeReceive.withLogUnhandled {
    case ReactiveActor.Messages.Changed => {
      Try {
        tasksUtil.process(MaxTasksPerIteration)
      } match {
        case Success(numberProcessed) => {
          if (numberProcessed >= MaxTasksPerIteration) {
            // process all pending tasks immediately
            self ! ReactiveActor.Messages.Changed
          }
        }
        case Failure(ex) => {
          if (env.mode == Mode.Test) {
            ex match {
              // Reduce verbosity of log in test for expected error on db connection closing
              case e: SQLException if e.getMessage.contains("has been closed") => // no-op
              case _ => logger.warn("Error processing tasks", ex)
            }
          } else {
            logger.warn("Error processing tasks", ex)
          }
        }
      }
    }
  }

}
