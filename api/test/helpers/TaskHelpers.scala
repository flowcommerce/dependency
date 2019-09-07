package helpers

import db.{InternalTask, InternalTasksDao}
import io.flow.dependency.v0.models.{Project, SyncType, TaskData, TaskDataSync, TaskDataSyncOne}
import io.flow.test.utils.FlowPlaySpec

trait TaskHelpers {
  self: FlowPlaySpec =>
  def generatedTasksDao: db.generated.TasksDao = init[db.generated.TasksDao]
  def internalTasksDao: InternalTasksDao = init[InternalTasksDao]

  def createTask(data: TaskData = makeTaskDataSync()): InternalTask = {
    val id = internalTasksDao.create(data)
    internalTasksDao.findById(id).get
  }

  def makeTaskDataSync(typ: Option[SyncType] = None): TaskDataSync = {
    TaskDataSync(
      `type` = typ
    )
  }

  def makeTaskDataSyncOne(project: Project): TaskDataSyncOne = {
    TaskDataSyncOne(
      `type` = SyncType.Project,
      id = project.id
    )
  }

}