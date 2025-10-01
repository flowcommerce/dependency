package db.generated

import akka.actor.ActorRef
import anorm.JodaParameterMetaData._
import anorm._
import io.flow.common.v0.models.UserReference
import io.flow.event.actors.ReactiveActor
import io.flow.postgresql.{OrderBy, Query}
import io.flow.util.IdGenerator
import java.sql.Connection
import javax.inject.{Inject, Named, Singleton}
import org.joda.time.DateTime
import play.api.db.Database
import scala.concurrent.duration.FiniteDuration

case class Task(
  id: String,
  data: String,
  priority: Int,
  numAttempts: Int,
  processedAt: Option[DateTime],
  createdAt: DateTime,
  updatedByUserId: String
) {

  lazy val form: TaskForm = TaskForm(
    data = data,
    priority = priority,
    numAttempts = numAttempts,
    processedAt = processedAt
  )

}

case class TaskForm(
  data: String,
  priority: Int,
  numAttempts: Int,
  processedAt: Option[DateTime]
)

object TasksTable {
  val Schema: String = "public"
  val Name: String = "tasks"
  val QualifiedName: String = s"$Schema.$Name"

  object Columns {
    val Id: String = "id"
    val Data: String = "data"
    val Priority: String = "priority"
    val NumAttempts: String = "num_attempts"
    val ProcessedAt: String = "processed_at"
    val CreatedAt: String = "created_at"
    val UpdatedByUserId: String = "updated_by_user_id"
    val UpdatedAt: String = "updated_at"
    val HashCode: String = "hash_code"
    val all: List[String] = List(Id, Data, Priority, NumAttempts, ProcessedAt, CreatedAt, UpdatedByUserId, UpdatedAt, HashCode)
  }
}

trait BaseTasksDao {

  def db: Database

  private val BaseQuery = Query("""
      | select tasks.id,
      |        tasks.data,
      |        tasks.priority,
      |        tasks.num_attempts,
      |        tasks.processed_at,
      |        tasks.created_at,
      |        tasks.updated_by_user_id,
      |        tasks.updated_at,
      |        tasks.hash_code
      |   from tasks
  """.stripMargin)

  def findById(id: String): Option[Task] = {
    db.withConnection { c =>
      findByIdWithConnection(c, id)
    }
  }

  def findByIdWithConnection(c: java.sql.Connection, id: String): Option[Task] = {
    findAllWithConnection(c, ids = Some(Seq(id)), limit = Some(1L)).headOption
  }

  def findAllByIds(
    ids: Seq[String],
    limit: Option[Long],
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("tasks.id"),
    timeout: Option[FiniteDuration] = None,
  ): Seq[Task] = {
    db.withConnection { c =>
      findAllByIdsWithConnection(
        c,
        ids = ids,
        limit = limit,
        offset = offset,
        orderBy = orderBy,
        timeout = timeout,
      )
    }
  }

  def findAllByIdsWithConnection(
    c: java.sql.Connection,
    ids: Seq[String],
    limit: Option[Long],
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("tasks.id"),
    timeout: Option[FiniteDuration] = None,
  ): Seq[Task] = {
      findAllWithConnection(
        c,
        ids = Some(ids),
        limit = limit,
        offset = offset,
        orderBy = orderBy,
        timeout = timeout,
      )
  }

  def findAllByNumAttempts(
    numAttempts: Option[Int] = None,
    numAttemptsGreaterThanOrEquals: Option[Int] = None,
    numAttemptsGreaterThan: Option[Int] = None,
    numAttemptsLessThanOrEquals: Option[Int] = None,
    numAttemptsLessThan: Option[Int] = None,
    ids: Option[Seq[String]] = None,
    limit: Option[Long],
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("tasks.id"),
    timeout: Option[FiniteDuration] = None,
  ): Seq[Task] = {
    db.withConnection { c =>
      findAllByNumAttemptsWithConnection(
        c,
        numAttempts = numAttempts,
        numAttemptsGreaterThanOrEquals = numAttemptsGreaterThanOrEquals,
        numAttemptsGreaterThan = numAttemptsGreaterThan,
        numAttemptsLessThanOrEquals = numAttemptsLessThanOrEquals,
        numAttemptsLessThan = numAttemptsLessThan,
        ids = ids,
        limit = limit,
        offset = offset,
        orderBy = orderBy,
        timeout = timeout,
      )
    }
  }

  def findAllByNumAttemptsWithConnection(
    c: java.sql.Connection,
    numAttempts: Option[Int] = None,
    numAttemptsGreaterThanOrEquals: Option[Int] = None,
    numAttemptsGreaterThan: Option[Int] = None,
    numAttemptsLessThanOrEquals: Option[Int] = None,
    numAttemptsLessThan: Option[Int] = None,
    ids: Option[Seq[String]] = None,
    limit: Option[Long],
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("tasks.id"),
    timeout: Option[FiniteDuration] = None,
  ): Seq[Task] = {
      findAllWithConnection(
        c,
        numAttempts = numAttempts,
        numAttemptsGreaterThanOrEquals = numAttemptsGreaterThanOrEquals,
        numAttemptsGreaterThan = numAttemptsGreaterThan,
        numAttemptsLessThanOrEquals = numAttemptsLessThanOrEquals,
        numAttemptsLessThan = numAttemptsLessThan,
        ids = ids,
        limit = limit,
        offset = offset,
        orderBy = orderBy,
        timeout = timeout,
      )
  }

  def findAllByNumAttemptsProcessedAts(
    numAttemptsProcessedAts: Seq[(Int, DateTime)],
    limit: Option[Long],
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("tasks.id"),
    timeout: Option[FiniteDuration] = None,
  ): Seq[Task] = {
    db.withConnection { c =>
      findAllByNumAttemptsProcessedAtsWithConnection(
        c,
        numAttemptsProcessedAts = numAttemptsProcessedAts,
        limit = limit,
        offset = offset,
        orderBy = orderBy,
        timeout = timeout,
      )
    }
  }

  def findAllByNumAttemptsProcessedAtsWithConnection(
    c: java.sql.Connection,
    numAttemptsProcessedAts: Seq[(Int, DateTime)],
    limit: Option[Long],
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("tasks.id"),
    timeout: Option[FiniteDuration] = None,
  ): Seq[Task] = {
      findAllWithConnection(
        c,
        numAttemptsProcessedAts = Some(numAttemptsProcessedAts),
        limit = limit,
        offset = offset,
        orderBy = orderBy,
        timeout = timeout,
      )
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
    numAttemptsProcessedAts: Option[Seq[(Int, DateTime)]] = None,
    pageSize: Long = 2000L,
    timeout: Option[FiniteDuration] = None,
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Iterator[Task] = {
    def iterate(lastValue: Option[Task]): Iterator[Task] = {
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
        numAttemptsProcessedAts = numAttemptsProcessedAts,
        limit = Some(pageSize),
        orderBy = OrderBy("tasks.id"),
        timeout = timeout,
      ) { q => customQueryModifier(q).greaterThan("tasks.id", lastValue.map(_.id)) }

      page.lastOption match {
        case None => Iterator.empty
        case lastValue => page.iterator ++ iterate(lastValue)
      }
    }

    iterate(None)
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
    numAttemptsProcessedAts: Option[Seq[(Int, DateTime)]] = None,
    limit: Option[Long],
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("tasks.id"),
    timeout: Option[FiniteDuration] = None,
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Seq[Task] = {
    db.withConnection { c =>
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
        numAttemptsProcessedAts = numAttemptsProcessedAts,
        limit = limit,
        offset = offset,
        orderBy = orderBy,
        timeout = timeout,
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
    numAttemptsProcessedAts: Option[Seq[(Int, DateTime)]] = None,
    limit: Option[Long],
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("tasks.id"),
    timeout: Option[FiniteDuration] = None,
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
      optionalIn2(("tasks.num_attempts", "tasks.processed_at"), numAttemptsProcessedAts).
      optionalLimit(limit).
      offset(offset).
      orderBy(orderBy.sql).
      timeout(timeout).
      as(TasksDao.parser.*)(c)
  }

}

object TasksDao {

  val parser: RowParser[Task] = {
    SqlParser.str("id") ~
    SqlParser.str("data") ~
    SqlParser.int("priority") ~
    SqlParser.int("num_attempts") ~
    SqlParser.get[DateTime]("processed_at").? ~
    SqlParser.get[DateTime]("created_at") ~
    SqlParser.str("updated_by_user_id") map {
      case id ~ data ~ priority ~ numAttempts ~ processedAt ~ createdAt ~ updatedByUserId => Task(
        id = id,
        data = data,
        priority = priority,
        numAttempts = numAttempts,
        processedAt = processedAt,
        createdAt = createdAt,
        updatedByUserId = updatedByUserId
      )
    }
  }

}

@Singleton
class TasksDao @Inject() (
  @Named("task-actor") val taskActor: ActorRef,
  override val db: Database
) extends BaseTasksDao {

  private val idGenerator = IdGenerator("tsk")

  def randomId(): String = idGenerator.randomId()

  private val InsertQuery = Query("""
    | insert into tasks
    | (id, data, priority, num_attempts, processed_at, updated_by_user_id, hash_code)
    | values
    | ({id}, {data}, {priority}::int, {num_attempts}::int, {processed_at}::timestamptz, {updated_by_user_id}, {hash_code}::bigint)
  """.stripMargin)

  private val UpdateQuery = Query("""
    | update tasks
    |    set data = {data},
    |        priority = {priority}::int,
    |        num_attempts = {num_attempts}::int,
    |        processed_at = {processed_at}::timestamptz,
    |        updated_by_user_id = {updated_by_user_id},
    |        hash_code = {hash_code}::bigint
    |  where id = {id}
    |    and tasks.hash_code != {hash_code}::bigint
  """.stripMargin)

  private def bindQuery(query: Query, form: TaskForm): Query = {
    query.
      bind("data", form.data).
      bind("priority", form.priority).
      bind("num_attempts", form.numAttempts).
      bind("processed_at", form.processedAt).
      bind("hash_code", form.hashCode())
  }

  private def toNamedParameter(updatedBy: UserReference, id: String, form: TaskForm): Seq[NamedParameter] = {
    Seq(
      "id" -> id,
      "data" -> form.data,
      "priority" -> form.priority,
      "num_attempts" -> form.numAttempts,
      "processed_at" -> form.processedAt,
      "updated_by_user_id" -> updatedBy.id,
      "hash_code" -> form.hashCode()
    )
  }

  def insert(updatedBy: UserReference, form: TaskForm): String = {
    val result = db.withConnection { c =>
      insert(c, updatedBy, form)
    }
    taskActor ! io.flow.dependency.actors.ReactiveActor.Messages.Changed
    result
  }

  def insert(c: Connection, updatedBy: UserReference, form: TaskForm): String = {
    val id = randomId()
    bindQuery(InsertQuery, form).
      bind("id", id).
      bind("updated_by_user_id", updatedBy.id).
      anormSql().execute()(c)
    id
  }

  def insertBatch(updatedBy: UserReference, forms: Seq[TaskForm]): Seq[String] = {
    val result = db.withConnection { c =>
      insertBatchWithConnection(c, updatedBy, forms)
    }
    sendChanged()
    result
  }

  def insertBatchWithConnection(c: Connection, updatedBy: UserReference, forms: Seq[TaskForm]): Seq[String] = {
    if (forms.nonEmpty) {
      val ids = forms.map(_ => randomId())
      val params = ids.zip(forms).map { case (id, form) => toNamedParameter(updatedBy, id, form) }
      BatchSql(InsertQuery.sql(), params.head, params.tail: _*).execute()(c)
      ids
    } else {
      Nil
    }
  }

  def updateIfChangedById(updatedBy: UserReference, id: String, form: TaskForm): Unit = {
    if (!findById(id).map(_.form).contains(form)) {
      updateById(updatedBy, id, form)
    }
  }

  def updateById(updatedBy: UserReference, id: String, form: TaskForm): Unit = {
    db.withConnection { c =>
      updateById(c, updatedBy, id, form)
    }
    taskActor ! io.flow.dependency.actors.ReactiveActor.Messages.Changed
  }

  def updateById(c: Connection, updatedBy: UserReference, id: String, form: TaskForm): Unit = {
    bindQuery(UpdateQuery, form).
      bind("id", id).
      bind("updated_by_user_id", updatedBy.id).
      anormSql().execute()(c)
    ()
  }

  def update(updatedBy: UserReference, existing: Task, form: TaskForm): Unit = {
    db.withConnection { c =>
      update(c, updatedBy, existing, form)
    }
    taskActor ! io.flow.dependency.actors.ReactiveActor.Messages.Changed
  }

  def update(c: Connection, updatedBy: UserReference, existing: Task, form: TaskForm): Unit = {
    updateById(c, updatedBy, existing.id, form)
  }

  def updateBatch(updatedBy: UserReference, idsAndForms: Seq[(String, TaskForm)]): Unit = {
    db.withConnection { c =>
      updateBatchWithConnection(c, updatedBy, idsAndForms)
    }
    sendChanged()
  }

  def updateBatchWithConnection(c: Connection, updatedBy: UserReference, idsAndForms: Seq[(String, TaskForm)]): Unit = {
    if (idsAndForms.nonEmpty) {
      val params = idsAndForms.map { case (id, form) => toNamedParameter(updatedBy, id, form) }
      BatchSql(UpdateQuery.sql(), params.head, params.tail: _*).execute()(c)
      ()
    }
  }

  def sendChanged(): Unit = {
    taskActor ! ReactiveActor.Messages.Changed
  }

  def delete(deletedBy: UserReference, task: Task): Unit = {
    db.withConnection { c =>
      delete(c, deletedBy, task)
    }
    taskActor ! io.flow.dependency.actors.ReactiveActor.Messages.Changed
  }

  def delete(c: Connection, deletedBy: UserReference, task: Task): Unit = {
    deleteById(c, deletedBy, task.id)
  }

  def deleteById(deletedBy: UserReference, id: String): Unit = {
    db.withConnection { c =>
      deleteById(c, deletedBy, id)
    }
    taskActor ! io.flow.dependency.actors.ReactiveActor.Messages.Changed
  }

  def deleteById(c: Connection, deletedBy: UserReference, id: String): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from tasks")
      .equals("id", id)
      .anormSql().executeUpdate()(c)
    ()
  }

  def deleteAllByIds(deletedBy: UserReference, ids: Seq[String]): Unit = {
    db.withConnection { c =>
      deleteAllByIds(c, deletedBy, ids)
    }
    sendChanged()
  }

  def deleteAllByIds(c: Connection, deletedBy: UserReference, ids: Seq[String]): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from tasks")
      .in("id", ids)
      .anormSql().executeUpdate()(c)
    ()
  }

  def deleteAllByNumAttempts(deletedBy: UserReference, numAttempts: Int): Unit = {
    db.withConnection { c =>
      deleteAllByNumAttempts(c, deletedBy, numAttempts)
    }
    taskActor ! io.flow.dependency.actors.ReactiveActor.Messages.Changed
  }

  def deleteAllByNumAttempts(c: Connection, deletedBy: UserReference, numAttempts: Int): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from tasks")
      .equals("num_attempts", numAttempts)
      .anormSql().executeUpdate()(c)
    ()
  }

  def deleteAllByNumAttemptses(deletedBy: UserReference, numAttemptses: Seq[Int]): Unit = {
    db.withConnection { c =>
      deleteAllByNumAttemptses(c, deletedBy, numAttemptses)
    }
    sendChanged()
  }

  def deleteAllByNumAttemptses(c: Connection, deletedBy: UserReference, numAttemptses: Seq[Int]): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from tasks")
      .in("num_attempts", numAttemptses)
      .anormSql().executeUpdate()(c)
    ()
  }

  def deleteAllByNumAttemptsAndProcessedAt(deletedBy: UserReference, numAttempts: Int, processedAt: DateTime): Unit = {
    db.withConnection { c =>
      deleteAllByNumAttemptsAndProcessedAt(c, deletedBy, numAttempts, processedAt)
    }
    taskActor ! io.flow.dependency.actors.ReactiveActor.Messages.Changed
  }

  def deleteAllByNumAttemptsAndProcessedAt(c: Connection, deletedBy: UserReference, numAttempts: Int, processedAt: DateTime): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from tasks")
      .equals("num_attempts", numAttempts)
      .equals("processed_at", processedAt)
      .anormSql().executeUpdate()(c)
    ()
  }

  def deleteAllByNumAttemptsAndProcessedAts(deletedBy: UserReference, numAttempts: Int, processedAts: Seq[DateTime]): Unit = {
    db.withConnection { c =>
      deleteAllByNumAttemptsAndProcessedAts(c, deletedBy, numAttempts, processedAts)
    }
    taskActor ! io.flow.dependency.actors.ReactiveActor.Messages.Changed
  }

  def deleteAllByNumAttemptsAndProcessedAts(c: Connection, deletedBy: UserReference, numAttempts: Int, processedAts: Seq[DateTime]): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from tasks")
      .equals("num_attempts", numAttempts)
      .in("processed_at", processedAts)
      .anormSql().executeUpdate()(c)
    ()
  }

  private val ValidCharacters: Set[String] = "_-,.abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".split("").toSet
  private def isSafe(value: String): Boolean = value.trim.split("").forall(ValidCharacters.contains)
  def setJournalDeletedByUserId(c: Connection, deletedBy: UserReference): Unit = {
    assert(isSafe(deletedBy.id), s"Value '${deletedBy.id}' contains unsafe characters")
    Query(s"SET journal.deleted_by_user_id = '${deletedBy.id}'").anormSql().executeUpdate()(c)
    ()
  }

  def deleteAll(
    deletedBy: UserReference,
    ids: Option[Seq[String]],
    numAttempts: Option[Int],
    processedAt: Option[DateTime],
    hasProcessedAt: Option[Boolean]
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Int = {
    val result = db.withConnection { implicit c =>
      deleteAllWithConnection(
        c,
        deletedBy = deletedBy,
        ids = ids,
        numAttempts = numAttempts,
        processedAt = processedAt,
        hasProcessedAt = hasProcessedAt
      )(customQueryModifier)
    }
    sendChanged()
    result
  }

  def deleteAllWithConnection(
    c: java.sql.Connection,
    deletedBy: UserReference,
    ids: Option[Seq[String]],
    numAttempts: Option[Int],
    processedAt: Option[DateTime],
    hasProcessedAt: Option[Boolean]
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Int = {
    setJournalDeletedByUserId(c, deletedBy)

    val query = Query("delete from tasks")
    customQueryModifier(query)
      .optionalIn("tasks.id", ids)
      .equals("tasks.num_attempts", numAttempts)
      .equals("tasks.processed_at", processedAt)
      .nullBoolean("tasks.processed_at", hasProcessedAt)
      .anormSql()
      .executeUpdate()(c)
  }

}