package db

import io.flow.dependency.v0.models.{TaskDataSync, TaskDataSyncOne}
import io.flow.test.utils.FlowPlaySpec

class InternalTasksDaoSpec extends FlowPlaySpec
    with helpers.TaskHelpers
{

  "findAll by processed" in {
    val task1 = createTask()
    val task2 = createTask()
    val ids = Seq(task1.id, task2.id).sorted

    def findIds() = {
      internalTasksDao.findAll(
        ids = Some(ids),
        hasProcessedAt = Some(false),
        limit = None
      ).map(_.id)
    }

    findIds() must equal(ids)

    internalTasksDao.setProcessed(task2.id)
    findIds() must equal(Seq(task1.id))
  }

  "createSyncAllIfNotQueued" in {
    def findTaskDataSync() = {
      internalTasksDao.findAll(
        data = Some(TaskDataSync()),
        hasProcessedAt = Some(false),
        limit = None
      )
    }

    findTaskDataSync().foreach { t =>
      internalTasksDao.setProcessed(t.id)
    }

    findTaskDataSync().size must be(0)
    internalTasksDao.queueAll()
    internalTasksDao.queueAll()
    findTaskDataSync().size must be(1)
  }

  "createSyncIfNotQueued" in {
    val project1 = makeTaskDataSyncOneProject()
    val project2 = makeTaskDataSyncOneProject()
    def findTaskData(data: TaskDataSyncOne) = {
      internalTasksDao.findAll(
        data = Some(data),
        hasProcessedAt = Some(false),
        limit = None
      )
    }

    findTaskData(project1).size must be(0)
    findTaskData(project2).size must be(0)

    internalTasksDao.createSyncIfNotQueued(project1)
    internalTasksDao.createSyncIfNotQueued(project2)
    findTaskData(project1).size must be(1)
    findTaskData(project2).size must be(1)
  }
}