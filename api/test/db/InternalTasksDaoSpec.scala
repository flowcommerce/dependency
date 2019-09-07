package db

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
}