package db

import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.models._
import io.flow.dependency.v0.models.json._
import io.flow.postgresql.OrderBy
import javax.inject.Inject
import org.joda.time.DateTime
import play.api.libs.json.Json

object InternalTask {
  val HighestPriority = 0
  val MediumPriority = 5
  val LowestPriority = 10

  def assertPriorityValid(priority: Int): Unit = {
    assert(
      priority >= HighestPriority && priority <= LowestPriority,
      s"Invalid priority[$priority] - must be in range $LowestPriority - $HighestPriority"
    )
  }
}

case class InternalTask(db: generated.Task) {
  val id: String = db.id
  val data: TaskData = Json.parse(db.data).as[TaskData]
}

class InternalTasksDao @Inject() (
  dao: generated.TasksDao,
  staticUserProvider: StaticUserProvider
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
    dao
      .findAll(
        ids = ids,
        hasProcessedAt = hasProcessedAt,
        limit = limit,
        offset = offset,
        orderBy = orderBy
      ) { q =>
        q.equals("data", data.map(Json.toJson(_).toString))
      }
      .map(InternalTask.apply)
  }

  /** Create a new task for the specified data, returning the task id
    */
  def create(data: TaskData, priority: Int): String = {
    InternalTask.assertPriorityValid(priority)
    dao.insert(
      staticUserProvider.systemUser,
      db.generated.TaskForm(
        data = Json.toJson(data).toString,
        priority = priority,
        numAttempts = 0,
        processedAt = None
      )
    )
  }

  /** Create a new sync all task IFF there isn't one that is pending processing as this is an expensive task.
    */
  def queueAll(): Unit = {
    createSyncIfNotQueued(TaskDataSync(), priority = InternalTask.LowestPriority)
  }

  def queueBinary(binary: Binary, priority: Int = InternalTask.LowestPriority): Unit = {
    createSyncIfNotQueued(TaskDataSyncOne(binary.id, SyncType.Binary), priority = priority)
  }

  def queueLibrary(library: Library, priority: Int = InternalTask.LowestPriority): Unit = {
    createSyncIfNotQueued(TaskDataSyncOne(library.id, SyncType.Library), priority = priority)
  }

  def queueLibrariesByPrefix(user: UserReference, prefix: String, priority: Int = InternalTask.LowestPriority): Unit = {
    createSyncIfNotQueued(
      TaskDataSyncLibrariesByPrefix(
        userId = user.id,
        prefix = prefix
      ),
      priority = priority
    )
  }

  def queueOrganizationLibraries(organization: String, priority: Int = InternalTask.LowestPriority): Unit = {
    createSyncIfNotQueued(TaskDataSyncOrganizationLibraries(organization), priority = priority)
  }

  def queueProjects(projectIds: Seq[String], priority: Int = InternalTask.LowestPriority): Unit = {
    projectIds.distinct.foreach { projectId =>
      createSyncIfNotQueued(TaskDataSyncOne(projectId, SyncType.Project), priority = priority)
    }
  }

  def queueProject(project: Project, priority: Int = InternalTask.LowestPriority): Unit = {
    queueProjects(Seq(project.id), priority = priority)
  }

  def createUpserted(project: Project, priority: Int = InternalTask.LowestPriority): Unit = {
    createSyncIfNotQueued(TaskDataUpserted(project.id, SyncType.Project), priority = priority)
  }

  def createSyncIfNotQueued(taskData: TaskData, priority: Int = InternalTask.LowestPriority): Unit = {
    findAll(
      data = Some(taskData),
      hasProcessedAt = Some(false),
      limit = Some(1)
    ).headOption match {
      case None => {
        create(taskData, priority = priority)
      }
      case Some(existing) => {
        if (existing.db.priority > priority) {
          // lower priority value is processed first
          setPriority(existing, priority)
        }
      }
    }
    ()
  }

  private[this] def setPriority(task: InternalTask, priority: Int): Unit = {
    InternalTask.assertPriorityValid(priority)
    dao.update(
      staticUserProvider.systemUser,
      task.db,
      task.db.form.copy(
        priority = priority
      )
    )
  }

  def setProcessed(taskId: String): Unit = {
    dao.findById(taskId).foreach { t =>
      dao.update(
        staticUserProvider.systemUser,
        t,
        t.form.copy(
          numAttempts = t.numAttempts + 1,
          processedAt = Some(DateTime.now)
        )
      )
    }
  }

  def deleteAllNonProcessedTasks(createdOnOrBefore: DateTime): Unit = {
    dao.deleteAll(
      deletedBy = staticUserProvider.systemUser,
      ids = None,
      numAttempts = None,
      processedAt = None,
      hasProcessedAt = None
    ) { q =>
      q.isNull("processed_at")
      q.lessThanOrEquals("created_at", createdOnOrBefore)
    }
    ()
  }
}
