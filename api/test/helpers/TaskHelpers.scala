package helpers

import db.{InternalTask, InternalTasksDao}
import db.generated.TasksDao
import io.flow.dependency.v0.models.{SyncType, TaskData, TaskDataSync, TaskDataSyncOne}
import io.flow.test.utils.FlowPlaySpec
import org.joda.time.DateTime

trait TaskHelpers {
  self: FlowPlaySpec =>
  def generatedTasksDao: TasksDao = init[TasksDao]
  def internalTasksDao: InternalTasksDao = init[InternalTasksDao]

  def deleteAllNonProcessedTasks(): Unit = {
    internalTasksDao.deleteAllNonProcessedTasks(
      createdOnOrBefore = (DateTime.now.plusYears(1)),
    )
  }

  def createTask(data: TaskData = makeTaskDataSync()): InternalTask = {
    val id = internalTasksDao.create(data, priority = InternalTask.LowestPriority)
    internalTasksDao.findById(id).get
  }

  def makeTaskDataSync(typ: Option[SyncType] = None): TaskDataSync = {
    TaskDataSync(
      `type` = typ,
    )
  }

  def makeTaskDataSyncOneProject(projectId: String = createTestId()): TaskDataSyncOne = {
    TaskDataSyncOne(
      `type` = SyncType.Project,
      id = projectId,
    )
  }

}
