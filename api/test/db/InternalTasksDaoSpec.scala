package db

import io.flow.dependency.v0.models.{TaskDataSync, TaskDataSyncOne}
import io.flow.test.utils.FlowPlaySpec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

class InternalTasksDaoSpec extends FlowPlaySpec
    with helpers.TaskHelpers
    with BeforeAndAfterAll
    with Eventually with IntegrationPatience
{

  override def beforeAll(): Unit = {
    deleteAllNonProcessedTasks()
  }

  "findAll by processed" in {
    val task1 = createTask()
    val task2 = createTask()
    val ids = Seq(task1.id, task2.id).sorted
    internalTasksDao.setProcessed(task1.id)
    internalTasksDao.setProcessed(task2.id)

    def findIds(hasProcessed: Boolean) = {
      internalTasksDao.findAll(
        ids = Some(ids),
        hasProcessedAt = Some(hasProcessed),
        limit = None
      ).map(_.id).sorted
    }

    findIds(false) must equal(Nil)
    findIds(true) must equal(ids)
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
    eventually {
      findTaskData(project1).size must be(1)
      findTaskData(project2).size must be(1)
    }
  }
}