package lib

import actors.TaskExecutorActor
import db.InternalTasksDao
import io.flow.dependency.v0.models.{SyncType, TaskDataSync, TaskDataSyncOne, TaskDataUndefinedType}
import io.flow.log.RollbarLogger
import io.flow.postgresql.OrderBy
import javax.inject.Inject

class TasksUtil @Inject() (
  internalTasksDao: InternalTasksDao,
  logger: RollbarLogger,
  @javax.inject.Named("task-executor-actor") taskExecutorActor: akka.actor.ActorRef
) {
  def process(limit: Long): Unit = {
    internalTasksDao.findAll(
      hasProcessedAt = Some(false),
      limit = Some(limit),
      orderBy = OrderBy("num_attempts, created_at")
    ).foreach { t =>
      t.data match {
        case _: TaskDataSync => {
          taskExecutorActor ! TaskExecutorActor.Messages.SyncAll(taskId = t.id)
        }
        case data: TaskDataSyncOne => {
          data.`type` match {
            case SyncType.Binary => {
              taskExecutorActor ! TaskExecutorActor.Messages.SyncBinary(taskId = t.id, binaryId = data.id)
            }
            case SyncType.Library => {
              taskExecutorActor ! TaskExecutorActor.Messages.SyncLibrary(taskId = t.id, libraryId = data.id)
            }
            case SyncType.Project => {
              taskExecutorActor ! TaskExecutorActor.Messages.SyncProject(taskId = t.id, projectId = data.id)
            }
            case SyncType.UNDEFINED(other) => {
              logger.withKeyValue("type", other).warn("SyncType.UNDEFINED - marking task processed")
              internalTasksDao.setProcessed(t.id)
            }
          }
        }
        case TaskDataUndefinedType(other) => {
          logger.withKeyValue("type", other).warn("TaskDataUndefinedType")
        }
      }
    }
  }

}