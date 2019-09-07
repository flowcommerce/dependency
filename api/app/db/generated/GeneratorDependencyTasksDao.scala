package db.generated

import anorm._
import db.DbHelpers
import io.flow.postgresql.{OrderBy, Query}
import io.flow.util.IdGenerator
import java.sql.Connection
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.db.Database
import play.api.libs.json.{JsObject, JsValue, Json}

case class Task(
  id: String,
  discriminator: String,
  data: JsObject,
  numAttempts: Int,
  processedAt: Option[DateTime],
  createdAt: DateTime
) {

  lazy val form: TaskForm = TaskForm(
    discriminator = discriminator,
    data = data,
    numAttempts = numAttempts,
    processedAt = processedAt
  )

}

case class TaskForm(
  discriminator: String,
  data: JsValue,
  numAttempts: Int,
  processedAt: Option[DateTime]
) {
  assert(
    data.isInstanceOf[JsObject],
    s"Field[data] must be a JsObject and not a ${data.getClass.getName}"
  )
}

object TasksTable {
  val Schema: String = "public"
  val Name: String = "tasks"

  object Columns {
    val Id: String = "id"
    val Discriminator: String = "discriminator"
    val Data: String = "data"
    val NumAttempts: String = "num_attempts"
    val ProcessedAt: String = "processed_at"
    val CreatedAt: String = "created_at"
    val UpdatedAt: String = "updated_at"
    val UpdatedByUserId: String = "updated_by_user_id"
    val HashCode: String = "hash_code"
    val all: List[String] = List(Id, Discriminator, Data, NumAttempts, ProcessedAt, CreatedAt, UpdatedAt, UpdatedByUserId, HashCode)
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
      |        tasks.discriminator,
      |        tasks.data::text as data_text,
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
    | (id, discriminator, data, num_attempts, processed_at, updated_by_user_id, hash_code)
    | values
    | ({id}, {discriminator}, {data}::json, {num_attempts}::int, {processed_at}::timestamptz, {updated_by_user_id}, {hash_code}::bigint)
  """.stripMargin)

  private[this] val UpdateQuery = Query("""
    | update tasks
    |    set discriminator = {discriminator},
    |        data = {data}::json,
    |        num_attempts = {num_attempts}::int,
    |        processed_at = {processed_at}::timestamptz,
    |        updated_by_user_id = {updated_by_user_id},
    |        hash_code = {hash_code}::bigint
    |  where id = {id}
    |    and tasks.hash_code != {hash_code}::bigint
  """.stripMargin)

  private[this] def bindQuery(query: Query, form: TaskForm): Query = {
    query.
      bind("discriminator", form.discriminator).
      bind("data", form.data).
      bind("num_attempts", form.numAttempts).
      bind("processed_at", form.processedAt).
      bind("hash_code", form.hashCode())
  }

  def insert(updatedBy: String, form: TaskForm): String = {
    db.withConnection { implicit c =>
      insert(c, updatedBy, form)
    }
  }

  def insert(implicit c: Connection, updatedBy: String, form: TaskForm): String = {
    val id = randomId()
    bindQuery(InsertQuery, form).
      bind("id", id).
      bind("updated_by_user_id", updatedBy).
      anormSql.execute()
    id
  }

  def updateIfChangedById(updatedBy: String, id: String, form: TaskForm): Unit = {
    if (!findById(id).map(_.form).contains(form)) {
      updateById(updatedBy, id, form)
    }
  }

  def updateById(updatedBy: String, id: String, form: TaskForm): Unit = {
    db.withConnection { implicit c =>
      updateById(c, updatedBy, id, form)
    }
  }

  def updateById(implicit c: Connection, updatedBy: String, id: String, form: TaskForm): Unit = {
    bindQuery(UpdateQuery, form).
      bind("id", id).
      bind("updated_by_user_id", updatedBy).
      anormSql.execute()
    ()
  }

  def update(updatedBy: String, existing: Task, form: TaskForm): Unit = {
    db.withConnection { implicit c =>
      update(c, updatedBy, existing, form)
    }
  }

  def update(implicit c: Connection, updatedBy: String, existing: Task, form: TaskForm): Unit = {
    updateById(c, updatedBy, existing.id, form)
  }

  def delete(deletedBy: String, task: Task): Unit = {
    dbHelpers.delete(deletedBy, task.id)
  }

  def deleteById(deletedBy: String, id: String): Unit = {
    db.withConnection { implicit c =>
      deleteById(c, deletedBy, id)
    }
  }

  def deleteById(c: java.sql.Connection, deletedBy: String, id: String): Unit = {
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
    numAttempts: Option[Int] = None,
    numAttemptsGreaterThanOrEquals: Option[Int] = None,
    numAttemptsGreaterThan: Option[Int] = None,
    numAttemptsLessThanOrEquals: Option[Int] = None,
    numAttemptsLessThan: Option[Int] = None,
    processedAt: Option[DateTime] = None,
    hasProcessedAt: Option[Boolean] = None,
    processedAtGreaterThanOrEquals: Option[DateTime] = None,
    processedAtGreaterThan: Option[DateTime] = None,
    processedAtLessThanOrEquals: Option[DateTime] = None,
    processedAtLessThan: Option[DateTime] = None,
    discriminator: Option[String] = None,
    discriminators: Option[Seq[String]] = None,
    pageSize: Long = 25L,
    orderBy: OrderBy = OrderBy("tasks.id")
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Iterator[Task] = {
    def iterate(offset: Long): Iterator[Task] = {
      val page = findAll(
        ids = ids,
        numAttempts = numAttempts,
        numAttemptsGreaterThanOrEquals = numAttemptsGreaterThanOrEquals,
        numAttemptsGreaterThan = numAttemptsGreaterThan,
        numAttemptsLessThanOrEquals = numAttemptsLessThanOrEquals,
        numAttemptsLessThan = numAttemptsLessThan,
        processedAt = processedAt,
        hasProcessedAt = hasProcessedAt,
        processedAtGreaterThanOrEquals = processedAtGreaterThanOrEquals,
        processedAtGreaterThan = processedAtGreaterThan,
        processedAtLessThanOrEquals = processedAtLessThanOrEquals,
        processedAtLessThan = processedAtLessThan,
        discriminator = discriminator,
        discriminators = discriminators,
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
    numAttempts: Option[Int] = None,
    numAttemptsGreaterThanOrEquals: Option[Int] = None,
    numAttemptsGreaterThan: Option[Int] = None,
    numAttemptsLessThanOrEquals: Option[Int] = None,
    numAttemptsLessThan: Option[Int] = None,
    processedAt: Option[DateTime] = None,
    hasProcessedAt: Option[Boolean] = None,
    processedAtGreaterThanOrEquals: Option[DateTime] = None,
    processedAtGreaterThan: Option[DateTime] = None,
    processedAtLessThanOrEquals: Option[DateTime] = None,
    processedAtLessThan: Option[DateTime] = None,
    discriminator: Option[String] = None,
    discriminators: Option[Seq[String]] = None,
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
        numAttempts = numAttempts,
        numAttemptsGreaterThanOrEquals = numAttemptsGreaterThanOrEquals,
        numAttemptsGreaterThan = numAttemptsGreaterThan,
        numAttemptsLessThanOrEquals = numAttemptsLessThanOrEquals,
        numAttemptsLessThan = numAttemptsLessThan,
        processedAt = processedAt,
        hasProcessedAt = hasProcessedAt,
        processedAtGreaterThanOrEquals = processedAtGreaterThanOrEquals,
        processedAtGreaterThan = processedAtGreaterThan,
        processedAtLessThanOrEquals = processedAtLessThanOrEquals,
        processedAtLessThan = processedAtLessThan,
        discriminator = discriminator,
        discriminators = discriminators,
        limit = limit,
        offset = offset,
        orderBy = orderBy
      )(customQueryModifier)
    }
  }

  def findAllWithConnection(
    c: java.sql.Connection,
    ids: Option[Seq[String]] = None,
    numAttempts: Option[Int] = None,
    numAttemptsGreaterThanOrEquals: Option[Int] = None,
    numAttemptsGreaterThan: Option[Int] = None,
    numAttemptsLessThanOrEquals: Option[Int] = None,
    numAttemptsLessThan: Option[Int] = None,
    processedAt: Option[DateTime] = None,
    hasProcessedAt: Option[Boolean] = None,
    processedAtGreaterThanOrEquals: Option[DateTime] = None,
    processedAtGreaterThan: Option[DateTime] = None,
    processedAtLessThanOrEquals: Option[DateTime] = None,
    processedAtLessThan: Option[DateTime] = None,
    discriminator: Option[String] = None,
    discriminators: Option[Seq[String]] = None,
    limit: Option[Long],
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("tasks.id")
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Seq[Task] = {
    customQueryModifier(BaseQuery).
      optionalIn("tasks.id", ids).
      equals("tasks.num_attempts", numAttempts).
      greaterThanOrEquals("tasks.num_attempts", numAttemptsGreaterThanOrEquals).
      greaterThan("tasks.num_attempts", numAttemptsGreaterThan).
      lessThanOrEquals("tasks.num_attempts", numAttemptsLessThanOrEquals).
      lessThan("tasks.num_attempts", numAttemptsLessThan).
      equals("tasks.processed_at", processedAt).
      nullBoolean("tasks.processed_at", hasProcessedAt).
      greaterThanOrEquals("tasks.processed_at", processedAtGreaterThanOrEquals).
      greaterThan("tasks.processed_at", processedAtGreaterThan).
      lessThanOrEquals("tasks.processed_at", processedAtLessThanOrEquals).
      lessThan("tasks.processed_at", processedAtLessThan).
      equals("tasks.discriminator", discriminator).
      optionalIn("tasks.discriminator", discriminators).
      optionalLimit(limit).
      offset(offset).
      orderBy(orderBy.sql).
      as(TasksDao.parser.*)(c)
  }

}

object TasksDao {

  val parser: RowParser[Task] = {
    SqlParser.str("id") ~
    SqlParser.str("discriminator") ~
    SqlParser.str("data_text") ~
    SqlParser.int("num_attempts") ~
    SqlParser.get[DateTime]("processed_at").? ~
    SqlParser.get[DateTime]("created_at") map {
      case id ~ discriminator ~ data ~ numAttempts ~ processedAt ~ createdAt => Task(
        id = id,
        discriminator = discriminator,
        data = Json.parse(data).as[JsObject],
        numAttempts = numAttempts,
        processedAt = processedAt,
        createdAt = createdAt
      )
    }
  }

}