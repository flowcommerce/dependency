package db

import javax.inject.{Inject, Singleton}
import io.flow.dependency.v0.models.{Publication, Subscription, SubscriptionForm}
import io.flow.common.v0.models.UserReference
import io.flow.postgresql.{OrderBy, Query}
import io.flow.util.IdGenerator
import anorm._
import com.google.inject.Provider
import play.api.db._

@Singleton
class SubscriptionsDao @Inject()(
  db: Database,
  usersDaoProvider: Provider[UsersDao]
){

  private[this] val dbHelpers = DbHelpers(db, "subscriptions")

  private[this] val BaseQuery = Query(s"""
    select subscriptions.id,
           subscriptions.user_id,
           subscriptions.publication
      from subscriptions
  """)

  private[this] val UpsertQuery = """
    insert into subscriptions
    (id, user_id, publication, updated_by_user_id)
    values
    ({id}, {user_id}, {publication}, {updated_by_user_id})
    on conflict(user_id, publication)
    do nothing
  """

  private[this] val idGenerator = IdGenerator("sub")

  private[db] def validate(
    form: SubscriptionForm
  ): Seq[String] = {
    val userErrors = usersDaoProvider.get.findById(form.userId) match {
      case None => Seq("User not found")
      case Some(_) => Nil
    }

    val publicationErrors = form.publication match {
      case Publication.UNDEFINED(_) => Seq("Invalid publication")
      case _ => Nil
    }

    userErrors ++ publicationErrors
  }

  def upsertByUserIdAndPublication(createdBy: UserReference, form: SubscriptionForm): Either[Seq[String], Unit] = {
    validate(form) match {
      case Nil => {
        db.withConnection { implicit c =>
          SQL(UpsertQuery).on(
            "id" -> idGenerator.randomId(),
            "user_id" -> form.userId,
            "publication" -> form.publication.toString,
            "updated_by_user_id" -> createdBy.id
          ).execute()
        }
        Right(())
      }
      case errors => Left(errors)
    }
  }

  def delete(deletedBy: UserReference, subscription: Subscription): Unit = {
    dbHelpers.delete(deletedBy.id, subscription.id)
  }

  def findByUserIdAndPublication(
    userId: String,
    publication: Publication
  ): Option[Subscription] = {
    findAll(
      userId = Some(userId),
      publication = Some(publication),
      limit = 1
    ).headOption
  }

  def findById(id: String): Option[Subscription] = {
    findAll(id = Some(id), limit = 1).headOption
  }

  def findAll(
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    userId: Option[String] = None,
    identifier: Option[String] = None,
    publication: Option[Publication] = None,
    minHoursSinceLastEmail: Option[Int] = None,
    minHoursSinceRegistration: Option[Int] = None,
    orderBy: OrderBy = OrderBy("subscriptions.created_at"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[Subscription] = {
    db.withConnection { implicit c =>
      Standards.query(
        BaseQuery,
        tableName = "subscriptions",
        auth = Clause.True, // TODO
        id = id,
        ids = ids,
        orderBy = orderBy.sql,
        limit = limit,
        offset = offset
      ).
        equals("subscriptions.user_id", userId).
        optionalText("subscriptions.publication", publication).
        and(
          minHoursSinceLastEmail.map { _ => """
            not exists (select 1
                          from last_emails
                         where last_emails.user_id = subscriptions.user_id
                           and last_emails.publication = subscriptions.publication
                           and last_emails.created_at > now() - interval '1 hour' * {min_hours}::int)
          """.trim }
        ).bind("min_hours", minHoursSinceLastEmail).
        and(
          minHoursSinceRegistration.map { _ => """
            exists (select 1
                      from users
                     where users.id = subscriptions.user_id
                       and users.created_at <= now() - interval '1 hour' * {min_hours_since_registration}::int)
          """.trim }
        ).bind("min_hours_since_registration", minHoursSinceRegistration).
        and(
          identifier.map { _ =>
            "subscriptions.user_id in (select user_id from user_identifiers where value = trim({identifier}))"
          }
        ).bind("identifier", identifier).
        as(
          io.flow.dependency.v0.anorm.parsers.Subscription.parser().*
        )
    }
  }

}
