package db.generated

import anorm._
import db.DbHelpers
import io.flow.postgresql.{OrderBy, Query}
import io.flow.util.IdGenerator
import java.sql.Connection
import java.util.UUID
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.db.Database
import play.api.libs.json.{JsObject, JsValue, Json}

case class Task(
  id: String,
  task: JsObject,
  numAttempts: Int,
  processedAt: Option[DateTime],
  createdAt: DateTime
) {

  lazy val form: TaskForm = TaskForm(
    task = task,
    numAttempts = numAttempts,
    processedAt = processedAt
  )

}

case class TaskForm(
  task: JsValue,
  numAttempts: Int,
  processedAt: Option[DateTime]
) {
  assert(
    task.isInstanceOf[JsObject],
    s"Field[task] must be a JsObject and not a ${task.getClass.getName}"
  )
}

object TasksTable {
  val Schema: String = "public"
  val Name: String = "tasks"

  object Columns {
    val Id: String = "id"
    val Task: String = "task"
    val NumAttempts: String = "num_attempts"
    val ProcessedAt: String = "processed_at"
    val CreatedAt: String = "created_at"
    val UpdatedAt: String = "updated_at"
    val UpdatedByUserId: String = "updated_by_user_id"
    val HashCode: String = "hash_code"
    val all: List[String] = List(Id, Task, NumAttempts, ProcessedAt, CreatedAt, UpdatedAt, UpdatedByUserId, HashCode)
  }
}

@Singleton
class TasksDao @Inject() (
  val db: Database
) {

  private[this] val idGenerator = IdGenerator("tsk")

  def randomId(): String = idGenerator.randomId()

  private[this] val dbHelpers = DbHelpers(db, "tasks")

  private[this] val BaseQuery = Query("""
      | select tasks.id,
      |        tasks.task::text as task_text,
      |        tasks.num_attempts,
      |        tasks.processed_at,
      |        tasks.created_at,
      |        tasks.updated_at,
      |        tasks.updated_by_user_id,
      |        tasks.hash_code
      |   from tasks
  """.stripMargin)

  private[this] val InsertQuery = Query("""
    | insert into tasks
    | (id, task, num_attempts, processed_at, updated_by_user_id, hash_code)
    | values
    | ({id}, {task}::json, {num_attempts}::integer, {processed_at}::timestamptz, {updated_by_user_id}, {hash_code}::bigint)
  """.stripMargin)

  private[this] val UpdateQuery = Query("""
    | update tasks
    |    set task = {task}::json,
    |        num_attempts = {num_attempts}::integer,
    |        processed_at = {processed_at}::timestamptz,
    |        updated_by_user_id = {updated_by_user_id},
    |        hash_code = {hash_code}::bigint
    |  where id = {id}
    |    and tasks.hash_code != {hash_code}::bigint
  """.stripMargin)

  private[this] def bindQuery(query: Query, form: TaskForm): Query = {
    query.
      bind("task", form.task).
      bind("num_attempts", form.numAttempts).
      bind("processed_at", form.processedAt).
      bind("hash_code", form.hashCode())
  }

  def insert(updatedBy: UUID, form: TaskForm): String = {
    db.withConnection { implicit c =>
      insert(c, updatedBy, form)
    }
  }

  def insert(implicit c: Connection, updatedBy: UUID, form: TaskForm): String = {
    val id = randomId()
    bindQuery(InsertQuery, form).
      bind("id", id).
      bind("updated_by_guid", updatedBy).
      anormSql.execute()
    id
  }

  def updateIfChangedById(updatedBy: UUID, id: String, form: TaskForm): Unit = {
    if (!findById(id).map(_.form).contains(form)) {
      updateById(updatedBy, id, form)
    }
  }

  def updateById(updatedBy: UUID, id: String, form: TaskForm): Unit = {
    db.withConnection { implicit c =>
      updateById(c, updatedBy, id, form)
    }
  }

  def updateById(implicit c: Connection, updatedBy: UUID, id: String, form: TaskForm): Unit = {
    bindQuery(UpdateQuery, form).
      bind("id", id).
      bind("updated_by_guid", updatedBy).
      anormSql.execute()
    ()
  }

  def update(updatedBy: UUID, existing: Task, form: TaskForm): Unit = {
    db.withConnection { implicit c =>
      update(c, updatedBy, existing, form)
    }
  }

  def update(implicit c: Connection, updatedBy: UUID, existing: Task, form: TaskForm): Unit = {
    updateById(c, updatedBy, existing.id, form)
  }

  def delete(deletedBy: UUID, task: Task): Unit = {
    dbHelpers.delete(deletedBy, task.id)
  }

  def deleteById(deletedBy: UUID, id: String): Unit = {
    db.withConnection { implicit c =>
      deleteById(c, deletedBy, id)
    }
  }

  def deleteById(c: java.sql.Connection, deletedBy: UUID, id: String): Unit = {
    dbHelpers.delete(c, deletedBy, id)
  }

  def findById(id: String): Option[Task] = {
    db.withConnection { implicit c =>
      findByIdWithConnection(c, id)
    }
  }

  def findByIdWithConnection(c: java.sql.Connection, id: String): Option[Task] = {
    findAllWithConnection(c, ids = Some(Seq(id)), limit = Some(1L)).headOption
  }

  def iterateAll(
    ids: Option[Seq[String]] = None,
    pageSize: Long = 25L,
    orderBy: OrderBy = OrderBy("tasks.id")
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Iterator[Task] = {
    def iterate(offset: Long): Iterator[Task] = {
      val page = findAll(
        ids = ids,
        limit = Some(pageSize),
        offset = offset,
        orderBy = orderBy
      )(customQueryModifier)

      page.toIterator ++ {
          if (page.length == pageSize) iterate(offset + pageSize)
          else Iterator.empty
        }
    }

    iterate(0)
  }

  def findAll(
    ids: Option[Seq[String]] = None,
    limit: Option[Long],
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("tasks.id")
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Seq[Task] = {
    db.withConnection { implicit c =>
      findAllWithConnection(
        c,
        ids = ids,
        limit = limit,
        offset = offset,
        orderBy = orderBy
      )(customQueryModifier)
    }
  }

  def findAllWithConnection(
    c: java.sql.Connection,
    ids: Option[Seq[String]] = None,
    limit: Option[Long],
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("tasks.id")
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Seq[Task] = {
    customQueryModifier(BaseQuery).
      optionalIn("tasks.id", ids).
      optionalLimit(limit).
      offset(offset).
      orderBy(orderBy.sql).
      as(TasksDao.parser.*)(c)
  }

  def deleteAll(
    deletedBy: UUID,
    ids: Option[Seq[String]]
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Int = {
    db.withConnection { implicit c =>
      deleteAllWithConnection(
        c,
        deletedBy = deletedBy,
        ids = ids
      )(customQueryModifier)
    }
  }

  def deleteAllWithConnection(
    c: java.sql.Connection,
    deletedBy: UUID,
    ids: Option[Seq[String]]
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Int = {
    anorm.SQL(s"SET journal.deleted_by_user_id = '${deletedBy.id}'")
      .executeUpdate()(c)

    val query = Query("delete from tasks")
    customQueryModifier(query)
      .optionalIn("tasks.id", ids)
      .anormSql()
      .executeUpdate()(c)
  }

}

object TasksDao {

  val parser: RowParser[Task] = {
    SqlParser.str("id") ~
    SqlParser.str("task_text") ~
    SqlParser.int("num_attempts") ~
    SqlParser.get[DateTime]("processed_at").? ~
    SqlParser.get[DateTime]("created_at") map {
      case id ~ task ~ numAttempts ~ processedAt ~ createdAt => Task(
        id = id,
        task = Json.parse(task).as[JsObject],
        numAttempts = numAttempts,
        processedAt = processedAt,
        createdAt = createdAt
      )
    }
  }

}