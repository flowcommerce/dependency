package lib

import db.InternalTasksDao
import io.flow.dependency.v0.models.{TaskDataSync, TaskDataSyncOne, TaskDataUndefinedType}
import io.flow.log.RollbarLogger
import io.flow.postgresql.OrderBy
import javax.inject.Inject

class TasksUtil @Inject() (
  internalTasksDao: InternalTasksDao,
  logger: RollbarLogger
) {
  def process(limit: Long): Unit = {
    internalTasksDao.findAll(
      hasProcessedAt = Some(false),
      limit = Some(limit),
      orderBy = OrderBy("num_attempts, created_at")
    ).foreach { t =>
      t.data match {
        case data: TaskDataSync => {
          println(s"TaskDataSync: $data")
        }
        case data: TaskDataSyncOne => {
          println(s"TaskDataSyncOne: $data")
        }
        case TaskDataUndefinedType(other) => {
          logger.withKeyValue("type", other).warn("TaskDataUndefinedType")
        }
      }
    }
  }

}