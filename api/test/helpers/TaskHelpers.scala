package helpers

import db.{InternalTask, InternalTasksDao}
import io.flow.dependency.v0.models.{SyncType, TaskData, TaskDataSync, TaskDataSyncOne}
import io.flow.test.utils.FlowPlaySpec

trait TaskHelpers {
  self: FlowPlaySpec =>
  def generatedTasksDao: db.generated.TasksDao = init[db.generated.TasksDao]
  def internalTasksDao: InternalTasksDao = init[InternalTasksDao]

  def deleteAllNonProcessedTasks(): Unit = {
    generatedTasksDao.deleteAll(
      deletedBy = testUser,
      ids = None,
      numAttempts = None,
      processedAt = None,
      hasProcessedAt = None
    ) { q =>
      q.isNull("processed_at")
    }
    ()
  }

  def createTask(data: TaskData = makeTaskDataSync()): InternalTask = {
    val id = internalTasksDao.create(data, priority = InternalTask.LowestPriority)
    internalTasksDao.findById(id).get
  }

  def makeTaskDataSync(typ: Option[SyncType] = None): TaskDataSync = {
    TaskDataSync(
      `type` = typ
    )
  }

  def makeTaskDataSyncOneProject(projectId: String = createTestId()): TaskDataSyncOne = {
    TaskDataSyncOne(
      `type` = SyncType.Project,
      id = projectId
    )
  }

}