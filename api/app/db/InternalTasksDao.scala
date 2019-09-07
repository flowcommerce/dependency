package db

import io.flow.dependency.v0.models._
import io.flow.dependency.v0.models.json._
import io.flow.postgresql.OrderBy
import io.flow.util.Constants
import javax.inject.Inject
import org.joda.time.DateTime
import play.api.libs.json.Json

case class InternalTask(db: generated.Task) {
  val id: String = db.id
  val data: TaskData = Json.parse(db.data).as[TaskData]
}

class InternalTasksDao @Inject()(
  dao: generated.TasksDao
) {

  def findById(id: String): Option[InternalTask] = {
    findAll(ids = Some(Seq(id)), limit = None).headOption
  }

  def findAll(
    ids: Option[Seq[String]] = None,
    data: Option[TaskData] = None,
    limit: Option[Long],
    hasProcessedAt: Option[Boolean] = None,
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("tasks.id")
  ): Seq[InternalTask] = {
    dao.findAll(
      ids = ids,
      hasProcessedAt = hasProcessedAt,
      limit = limit,
      offset = offset,
      orderBy = orderBy
    ) { q =>
      q.equals("data", data.map(Json.toJson(_).toString))
    }.map(InternalTask.apply)
  }

  /**
   * Create a new task for the specified data,
   * returning the task id
   */
  def create(data: TaskData): String = {
    dao.insert(
      Constants.SystemUser.id,
      db.generated.TaskForm(
        data = Json.toJson(data).toString,
        numAttempts = 0,
        processedAt = None
      )
    )
  }

  /**
   * Create a new sync all task IFF there isn't one
   * that is pending processing as this is an expensive
   * task.
   */
  def createSyncAllIfNotQueued(): Unit = {
    createSyncIfNotQueued(TaskDataSync())
  }

  def createSyncIfNotQueued(binary: Binary): Unit = {
    createSyncIfNotQueued(TaskDataSyncOne(binary.id, SyncType.Binary))
  }

  def createSyncIfNotQueued(library: Library): Unit = {
    createSyncIfNotQueued(TaskDataSyncOne(library.id, SyncType.Library))
  }

  def createSyncIfNotQueued(project: Project): Unit = {
    createSyncIfNotQueued(TaskDataSyncOne(project.id, SyncType.Project))
  }

  def createSyncIfNotQueued(taskData: TaskData): Unit = {
    val existing = findAll(
      data = Some(taskData),
      hasProcessedAt = Some(false),
      limit = Some(1)
    )
    if (existing.isEmpty) {
      create(taskData)
      ()
    }
  }

  def setProcessed(taskId: String): Unit = {
    dao.findById(taskId).foreach { t =>
      dao.update(Constants.SystemUser.id, t, t.form.copy(
        processedAt = Some(DateTime.now)
      ))
    }
  }
}
