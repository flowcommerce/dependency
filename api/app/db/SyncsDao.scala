package db

import javax.inject.{Inject, Singleton}
import io.flow.dependency.v0.models.{Sync, SyncEvent}
import io.flow.common.v0.models.UserReference
import io.flow.postgresql.{OrderBy, Query}
import io.flow.util.IdGenerator
import anorm._
import play.api.db._

case class SyncForm(
  `type`: String,
  objectId: String,
  event: SyncEvent
)

@Singleton
class SyncsDao @Inject()(
  db: Database
) {

  private[this] val BaseQuery = Query(s"""
    select syncs.id,
           syncs.type,
           syncs.object_id,
           syncs.event,
           syncs.created_at
      from syncs
  """)

  private[this] val InsertQuery = """
    insert into syncs
    (id, type, object_id, event, updated_by_user_id)
    values
    ({id}, {type}, {object_id}, {event}, {updated_by_user_id})
  """

  private[this] val PurgeQuery = """
    delete from syncs where created_at < now() - interval '7 days'
  """

  def withStartedAndCompleted[T](
    createdBy: UserReference, `type`: String, id: String
  ) (
    f: => T
  ): T = {
    recordStarted(createdBy, `type`, id)
    val result = f
    recordCompleted(createdBy, `type`, id)
    result
  }

  def recordStarted(createdBy: UserReference, `type`: String, id: String): Unit = {
    createInternal(createdBy, SyncForm(`type`, id, SyncEvent.Started))
    ()
  }

  def recordCompleted(createdBy: UserReference, `type`: String, id: String): Unit = {
    createInternal(createdBy, SyncForm(`type`, id, SyncEvent.Completed))
    ()
  }

  def create(createdBy: UserReference, form: SyncForm): Sync = {
    val id = createInternal(createdBy, form)
    findById(id).getOrElse {
      sys.error("Failed to create sync")
    }
  }

  private[this] def createInternal(createdBy: UserReference, form: SyncForm): String = {
    val id = IdGenerator("syn").randomId()

    db.withConnection { implicit c =>
      SQL(InsertQuery).on(
        'id -> id,
        'type -> form.`type`,
        'object_id -> form.objectId,
        'event -> form.event.toString,
        'updated_by_user_id -> createdBy.id
      ).execute()
    }

    id
  }

  def purgeOld(): Unit = {
    db.withConnection { implicit c =>
      SQL(PurgeQuery).execute()
    }
    ()
  }

  def findById(id: String): Option[Sync] = {
    findAll(id = Some(id), limit = 1).headOption
  }

  def findAll(
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    objectId: Option[String] = None,
    event: Option[SyncEvent] = None,
    orderBy: OrderBy = OrderBy("-syncs.created_at"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[Sync] = {
    db.withConnection { implicit c =>
      Standards.query(
        BaseQuery,
        tableName = "syncs",
        auth = Clause.True, // TODO
        id = id,
        ids = ids,
        orderBy = orderBy.sql,
        limit = limit,
        offset = offset
      ).
        equals("syncs.object_id", objectId).
        optionalText("syncs.event", event).
        as(
          io.flow.dependency.v0.anorm.parsers.Sync.parser().*
        )
    }
  }

}
