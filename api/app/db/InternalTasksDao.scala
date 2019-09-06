package db

import io.flow.dependency.v0.models.Task
import io.flow.dependency.v0.models.json._
import io.flow.postgresql.{OrderBy, Query}
import javax.inject.{Inject, Singleton}

case class InternalTask(db: generated.Task) {
  val task: Task = task.as[Task]
}

class InternalTasksDao @Inject()(
  dao: generated.TasksDao
) {

  def findAll(
    ids: Option[Seq[String]] = None,
    limit: Option[Long],
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("tasks.id")
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Seq[InternalTask] = {
    dao.findAll(
      ids = ids,
      limit = limit,
      offset = offset,
      orderBy = orderBy
    )(customQueryModifier).map(InternalTask.apply)
  }

}
