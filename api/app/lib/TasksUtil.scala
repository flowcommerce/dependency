package lib

import db.InternalTasksDao
import javax.inject.Inject

class TasksUtil @Inject() (
  internalTasksDao: InternalTasksDao
) {
  def process(): Unit = {

  }
}