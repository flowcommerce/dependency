package db

import javax.inject.{Inject, Singleton}
import io.flow.dependency.v0.models.Reference
import io.flow.common.v0.models.UserReference
import io.flow.postgresql.{OrderBy, Query}
import io.flow.dependency.v0.models.Publication
import io.flow.util.IdGenerator
import org.joda.time.DateTime
import anorm._
import com.google.inject.Provider
import play.api.db._

case class LastEmailForm(
  userId: String,
  publication: Publication
)

case class LastEmail(
  id: String,
  user: Reference,
  publication: Publication,
  createdAt: DateTime
)

@Singleton
class LastEmailsDao @Inject()(
  db: Database
){
  private[this] val dbHelpers = DbHelpers(db, "last_emails")

  private[this] val BaseQuery = Query(s"""
    select last_emails.*
      from last_emails
  """)

  private[this] val InsertQuery = """
    insert into last_emails
    (id, user_id, publication, updated_by_user_id)
    values
    ({id}, {user_id}, {publication}, {updated_by_user_id})
  """

  def record(
    createdBy: UserReference,
    form: LastEmailForm
  ): LastEmail = {
    val id = db.withTransaction { implicit c =>
      findByUserIdAndPublication(form.userId, form.publication).foreach { rec =>
        dbHelpers.delete(c, createdBy.id, rec.id)
      }
      create(createdBy, form)
    }
    findById(id).getOrElse {
      sys.error("Failed to record last email")
    }
  }

  def delete(deletedBy: UserReference, rec: LastEmail): Unit = {
    dbHelpers.delete(deletedBy.id, rec.id)
  }

  private[this] def create(
    createdBy: UserReference,
    form: LastEmailForm
  ) (
    implicit c: java.sql.Connection
  ): String = {
    val id = IdGenerator("lse").randomId()
    SQL(InsertQuery).on(
      'id -> id,
      'user_id -> form.userId,
      'publication -> form.publication.toString,
      'updated_by_user_id -> createdBy.id
    ).execute()
    id
  }

  def findById(id: String): Option[LastEmail] = {
    findAll(id = Some(id), limit = 1).headOption
  }

  def findByUserIdAndPublication(userId: String, publication: Publication): Option[LastEmail] = {
    findAll(userId = Some(userId), publication = Some(publication), limit = 1).headOption
  }

  def findAll(
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    userId: Option[String] = None,
    publication: Option[Publication] = None,
    orderBy: OrderBy = OrderBy("-last_emails.publication, last_emails.created_at"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[LastEmail] = {

    db.withConnection { implicit c =>
      BaseQuery.
        equals("last_emails.id", id).
        optionalIn("last_emails.id", ids).
        equals("last_emails.user_id", userId).
        optionalText("last_emails.publication", publication).
        orderBy(orderBy.sql).
        limit(limit).
        offset(offset).
        as(parser.*)
    }
  }

  private[this] val parser: RowParser[LastEmail] = {
    SqlParser.str("id") ~
    io.flow.dependency.v0.anorm.parsers.Reference.parser("user_id") ~
    io.flow.dependency.v0.anorm.parsers.Publication.parser() ~
    SqlParser.get[DateTime]("created_at") map {
      case id ~ user ~ publication ~ createdAt => {
        LastEmail(
          id = id,
          user = user,
          publication = publication,
          createdAt = createdAt
        )
      }
    }
  }

}
