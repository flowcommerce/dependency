package lib

import actors.TaskExecutorActor
import db.InternalTasksDao
import io.flow.dependency.v0.models.{SyncType, TaskData, TaskDataSync, TaskDataSyncOne, TaskDataUndefinedType}
import io.flow.log.RollbarLogger
import io.flow.postgresql.OrderBy
import javax.inject.Inject

import scala.util.{Failure, Success, Try}

class TasksUtil @Inject() (
  internalTasksDao: InternalTasksDao,
  logger: RollbarLogger,
  @javax.inject.Named("task-executor-actor") taskExecutorActor: akka.actor.ActorRef
) {

  /**
   * Invokes f, ensuring the task is marked processed once the function
   * returns. Logs any exceptions.
   */
  def process(taskId: String)(f: => Any)(implicit logger: RollbarLogger): Unit = {
    Try {
      f
    } match {
      case Success(_) => // no-op
      case Failure(ex) => logger.withKeyValue("task_id", taskId).warn("Error processing task", ex)
    }
    internalTasksDao.setProcessed(taskId)
  }

  /**
   * @limit Max # of tasks to process on this iteration
   * @return Number of tasks processed
   */
  def process(limit: Long): Long = {
    val all = internalTasksDao.findAll(
      hasProcessedAt = Some(false),
      limit = Some(limit),
      orderBy = OrderBy("num_attempts, created_at")
    )
    all.foreach { t =>
      processData(t.id, t.data)
      internalTasksDao.setProcessed(t.id)
    }
    all.size.toLong
  }

  private[this] def processData(taskId: String, data: TaskData): Unit = {
    data match {
        case _: TaskDataSync => {
          taskExecutorActor ! TaskExecutorActor.Messages.SyncAll(taskId = taskId)
        }
        case data: TaskDataSyncOne => {
          data.`type` match {
            case SyncType.Binary => {
              taskExecutorActor ! TaskExecutorActor.Messages.SyncBinary(taskId = taskId, binaryId = data.id)
            }
            case SyncType.Library => {
              taskExecutorActor ! TaskExecutorActor.Messages.SyncLibrary(taskId = taskId, libraryId = data.id)
            }
            case SyncType.Project => {
              taskExecutorActor ! TaskExecutorActor.Messages.SyncProject(taskId = taskId, projectId = data.id)
            }
            case SyncType.UNDEFINED(other) => {
              logger.withKeyValue("type", other).warn("SyncType.UNDEFINED - marking task processed")
            }
          }
        }
        case TaskDataUndefinedType(other) => {
          logger.withKeyValue("type", other).warn("TaskDataUndefinedType")
        }
      }
  }

}