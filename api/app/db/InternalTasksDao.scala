package db

import io.flow.dependency.v0.models.TaskData
import io.flow.dependency.v0.models.json._
import io.flow.postgresql.{OrderBy, Query}
import io.flow.util.Constants
import javax.inject.Inject
import org.joda.time.DateTime

case class InternalTask(db: generated.Task) {
  val data: TaskData = db.data.as[TaskData]
}

class InternalTasksDao @Inject()(
  dao: generated.TasksDao
) {

  def findAll(
    ids: Option[Seq[String]] = None,
    limit: Option[Long],
    hasProcessedAt: Option[Boolean] = None,
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("tasks.id")
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Seq[InternalTask] = {
    dao.findAll(
      ids = ids,
      hasProcessedAt = hasProcessedAt,
      limit = limit,
      offset = offset,
      orderBy = orderBy
    )(customQueryModifier).map(InternalTask.apply)
  }

  def setProcessed(taskId: String): Unit = {
    dao.findById(taskId).foreach { t =>
      dao.update(Constants.SystemUser.id, t, t.form.copy(
        processedAt = Some(DateTime.now)
      ))
    }
  }
}
