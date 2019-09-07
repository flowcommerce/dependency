package db

import io.flow.dependency.v0.models.TaskDataDiscriminator
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
    0.to(3).foreach { _ =>
      internalTasksDao.createSyncAllIfNotQueued()
    }
    def findTaskDataSync() = {
      internalTasksDao.findAll(
        discriminator = Some(TaskDataDiscriminator.TaskDataSync),
        hasProcessedAt = Some(false),
        limit = None
      )
    }

    findTaskDataSync().foreach { t =>
      internalTasksDao.setProcessed(t.id)
    }

    findTaskDataSync().size must be(0)
    internalTasksDao.createSyncAllIfNotQueued()
    internalTasksDao.createSyncAllIfNotQueued()
    findTaskDataSync().size must be(1)
  }
}