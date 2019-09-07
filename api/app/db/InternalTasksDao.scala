package db

import io.flow.dependency.v0.models.TaskData
import io.flow.dependency.v0.models.json._
import io.flow.event.JsonUtil
import io.flow.postgresql.{OrderBy, Query}
import io.flow.util.Constants
import javax.inject.Inject
import org.joda.time.DateTime
import play.api.libs.json.Json

case class InternalTask(db: generated.Task) {
  val id: String = db.id
  val data: TaskData = db.data.as[TaskData]
}

class InternalTasksDao @Inject()(
  dao: generated.TasksDao
) {

  def findById(id: String): Option[InternalTask] = {
    findAll(ids = Some(Seq(id)), limit = None).headOption
  }

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

  /**
   * Create a new task for the specified data,
   * returning the task id
   */
  def create(data: TaskData): String = {
    val dataJson = Json.toJson(data)
    dao.insert(
      Constants.SystemUser.id,
      db.generated.TaskForm(
        discriminator = JsonUtil.requiredString(dataJson, "discriminator"),
        data = dataJson,
        numAttempts = 0,
        processedAt = None
      )
    )
  }

  def setProcessed(taskId: String): Unit = {
    dao.findById(taskId).foreach { t =>
      dao.update(Constants.SystemUser.id, t, t.form.copy(
        processedAt = Some(DateTime.now)
      ))
    }
  }
}
