package io.flow.dependency.actors

import java.sql.SQLException

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import db.InternalTask
import io.flow.akka.SafeReceive
import io.flow.akka.recurring.{ScheduleConfig, Scheduler}
import io.flow.dependency.v0.models.{SyncType, TaskDataSync, TaskDataSyncLibrariesByPrefix, TaskDataSyncOne, TaskDataUpserted}
import io.flow.log.RollbarLogger
import io.flow.play.util.ApplicationConfig
import javax.inject.Inject
import lib.TasksUtil
import play.api.{Environment, Mode}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class TaskActorParameters @Inject()(
  env: Environment,
  val system: ActorSystem,
  val tasksUtil: TasksUtil,
  val config: ApplicationConfig,
  val logger: RollbarLogger
) {
  val isTest: Boolean = env.mode == Mode.Test
}

/**
 * Receives notification of change and forwards on to all task actors
 */
class TaskActor @Inject()(
  rollbar: RollbarLogger,
  @javax.inject.Named("task-actor-upserted") taskActorUpserted: ActorRef,
  @javax.inject.Named("task-actor-sync-all") taskActorSyncAll: ActorRef,
  @javax.inject.Named("task-actor-sync-one-binary") taskActorSyncOneBinary: ActorRef,
  @javax.inject.Named("task-actor-sync-one-library") taskActorSyncOneLibrary: ActorRef,
  @javax.inject.Named("task-actor-sync-one-project") taskActorSyncOneProject: ActorRef,
  @javax.inject.Named("task-actor-sync-organization-libraries") taskActorSyncOrganizationLibraries: ActorRef,
  @javax.inject.Named("task-actor-sync-libraries-by-prefix") taskActorSyncLibrariesByPrefix: ActorRef,
) extends Actor {

  private[this] implicit val logger: RollbarLogger = rollbar.fingerprint(getClass.getName)
  private[this] val allActors = Seq(
    taskActorUpserted, taskActorSyncAll,
    taskActorSyncOneBinary, taskActorSyncOneLibrary, taskActorSyncOneProject,
    taskActorSyncOrganizationLibraries, taskActorSyncLibrariesByPrefix,
  )

  def receive: Receive = SafeReceive.withLogUnhandled {
    case ReactiveActor.Messages.Changed => {
      allActors.foreach { actorRef =>
        actorRef ! ReactiveActor.Messages.Changed
      }
    }
  }
}

abstract class BaseTaskActor @Inject()(
  params: TaskActorParameters,
  dispatcherName: String
) extends Actor with ActorLogging with Scheduler {

  def accepts(task: InternalTask): Boolean

  private[this] implicit val logger: RollbarLogger = params.logger.fingerprint(getClass.getName)
  private[this] implicit val ec: ExecutionContext = params.system.dispatchers.lookup(dispatcherName)

  private[this] val MaxTasksPerIteration = 100L

  scheduleRecurring(
    ScheduleConfig.fromConfig(params.config.underlying.underlying, "io.flow.dependency.api.task.changed"),
    ReactiveActor.Messages.Changed
  )

  def receive: Receive = SafeReceive.withLogUnhandled {
    case ReactiveActor.Messages.Changed => processChanged()
  }

  private[this] def processChanged(): Unit = {
    Try {
      params.tasksUtil.process(MaxTasksPerIteration)(accepts)
    } match {
      case Success(numberProcessed) => {
        if (numberProcessed >= MaxTasksPerIteration) {
          // process all pending tasks immediately
          self ! ReactiveActor.Messages.Changed
        }
      }
      case Failure(ex) => {
        if (params.isTest) {
          ex match {
            // Reduce verbosity of log in test for expected error on db connection closing
            case e: SQLException if e.getMessage.contains("has been closed") => // no-op
            case _ => params.logger.warn("Error processing tasks", ex)
          }
        } else {
          params.logger.warn("Error processing tasks", ex)
        }
      }
    }
  }
}

class TaskActorSyncAll @Inject()(
  params: TaskActorParameters
) extends BaseTaskActor(
  params,
  "tasks-sync-all-actor-context"
) {
  override def accepts(task: InternalTask): Boolean = {
    task.data match {
      case _: TaskDataSync => true
      case _ => false
    }
  }
}

class TaskActorSyncOneProject @Inject()(
  params: TaskActorParameters
) extends BaseTaskActor(
  params,
  "tasks-sync-one-project-actor-context"
) {
  override def accepts(task: InternalTask): Boolean = {
    task.data match {
      case s: TaskDataSyncOne => s.`type` == SyncType.Project
      case _ => false
    }
  }
}

class TaskActorSyncOneBinary @Inject()(
  params: TaskActorParameters
) extends BaseTaskActor(
  params,
  "tasks-sync-one-binary-actor-context"
) {
  override def accepts(task: InternalTask): Boolean = {
    task.data match {
      case s: TaskDataSyncOne => s.`type` == SyncType.Binary
      case _ => false
    }
  }
}

class TaskActorSyncOneLibrary @Inject()(
  params: TaskActorParameters
) extends BaseTaskActor(
  params,
  "tasks-sync-one-library-actor-context"
) {
  override def accepts(task: InternalTask): Boolean = {
    task.data match {
      case s: TaskDataSyncOne => s.`type` == SyncType.Library
      case _ => false
    }
  }
}

class TaskActorSyncOrganizationLibraries @Inject()(
  params: TaskActorParameters
) extends BaseTaskActor(
  params,
  "tasks-sync-organization-libraries-context"
) {
  override def accepts(task: InternalTask): Boolean = {
    task.data match {
      case _: TaskDataSyncLibrariesByPrefix => true
      case _ => false
    }
  }
}

class TaskActorSyncLibrariesByPrefix @Inject()(
  params: TaskActorParameters
) extends BaseTaskActor(
  params,
  "tasks-sync-libraries-by-prefix-context"
) {
  override def accepts(task: InternalTask): Boolean = {
    task.data match {
      case _: TaskActorSyncLibrariesByPrefix => true
      case _ => false
    }
  }
}

class TaskActorUpserted @Inject()(
  params: TaskActorParameters
) extends BaseTaskActor(
  params,
  "tasks-upserted-actor-context"
) {
  override def accepts(task: InternalTask): Boolean = {
    task.data match {
      case _: TaskDataUpserted => true
      case _ => false
    }
  }
}
