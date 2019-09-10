package lib

import db.InternalTasksDao
import io.flow.log.RollbarLogger
import javax.inject.Inject

import scala.util.{Failure, Success, Try}

class TaskProcessor @Inject()(
  internalTasksDao: InternalTasksDao,
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
}