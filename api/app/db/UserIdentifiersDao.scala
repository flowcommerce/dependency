package db

import javax.inject.{Inject, Singleton}
import io.flow.dependency.v0.models.UserIdentifier
import io.flow.common.v0.models.{User, UserReference}
import io.flow.postgresql.{OrderBy, Query}
import io.flow.dependency.api.lib.Recipient
import io.flow.util.IdGenerator
import anorm._
import play.api.db._

@Singleton
class UserIdentifiersDao @Inject()(
  db: Database,
  staticUserProvider: StaticUserProvider
){

  val GithubOauthUserIdentifierValue = "github_oauth"

  private[this] val dbHelpers = DbHelpers(db, "user_identifiers")

  private[this] val BaseQuery = Query(s"""
    select user_identifiers.id,
           user_identifiers.user_id,
           user_identifiers.value
      from user_identifiers
  """)

  private[this] val InsertQuery = """
    insert into user_identifiers
    (id, user_id, value, updated_by_user_id)
    values
    ({id}, {user_id}, {value}, {updated_by_user_id})
  """

  /**
    * Returns the latest identifier, creating if necessary
    */
  def latestForUser(createdBy: UserReference, user: User): UserIdentifier = {
    findAll(userId = Some(user.id))
      .headOption
      .getOrElse(createForUser(createdBy, user))
  }

  def recipientForUser(user: User): Option[Recipient] = {
    user.email.map { email =>
      Recipient(
        email = email,
        name = user.name,
        userId = user.id,
        identifier = latestForUser(staticUserProvider.systemUser, user).value
      )
    }
  }

  def createForUser(createdBy: UserReference, user: User): UserIdentifier = {
    db.withConnection { implicit c =>
      createWithConnection(createdBy, user)
    }
  }

  private[this] val IdentifierLength = 60
  private[this] val random = new scala.util.Random
  private[this] val Characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
  private[this] val Numbers = "0123456789"
  private[this] val CharactersAndNumbers = Characters + Numbers

  private[this] def randomString(alphabet: String)(n: Int): String = {
    List.fill(n)(random.nextInt(alphabet.length)).map(alphabet).mkString
  }

  /**
    * Generate a unique random string of length
    * IdentifierLength. Guaranteed to start with a letter (to avoid
    * any issues with leading zeroes)
    */
  private[this] def generateIdentifier(): String = {
    randomString(Characters)(1) +randomString(CharactersAndNumbers)(IdentifierLength - 1)
  }

  private[this] def createWithConnection(createdBy: UserReference, user: User)(implicit c: java.sql.Connection): UserIdentifier = {
    val id = IdGenerator("usi").randomId()

    SQL(InsertQuery).on(
      'id -> id,
      'user_id -> user.id,
      'value -> generateIdentifier(),
      'updated_by_user_id -> createdBy.id
    ).execute()

    findAllWithConnection(id = Some(id), limit = 1).headOption.getOrElse {
      sys.error("Failed to create identifier")
    }
  }

  def delete(deletedBy: UserReference, identifier: UserIdentifier): Unit = {
    dbHelpers.delete(deletedBy.id, identifier.id)
  }

  def findById(id: String): Option[UserIdentifier] = {
    findAll(id = Some(id), limit = 1).headOption
  }

  def findAll(
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    userId: Option[String] = None,
    value: Option[String] = None,
    limit: Long = 25,
    offset: Long = 0
  ): Seq[UserIdentifier] = {
    db.withConnection { implicit c =>
      findAllWithConnection(
        id = id,
        ids = ids,
        userId = userId,
        value = value,
        limit = limit,
        offset = offset
      )
    }
  }

  private[this] def findAllWithConnection(
    id: Option[String],
    ids: Option[Seq[String]] = None,
    userId: Option[String] = None,
    value: Option[String] = None,
    orderBy: OrderBy = OrderBy("-user_identifiers.created_at"),
    limit: Long,
    offset: Long = 0
  )(implicit c: java.sql.Connection): Seq[UserIdentifier] = {
    Standards.query(
      BaseQuery,
      tableName = "user_identifiers",
      auth = Clause.True, // TODO. Right now we ignore auth as there is no way to filter to users
      id = id,
      ids = ids,
      orderBy = orderBy.sql,
      limit = limit,
      offset = offset
    ).
      equals("user_identifiers.user_id", userId).
      optionalText("user_identifiers.value", value).
      as(
        io.flow.dependency.v0.anorm.parsers.UserIdentifier.parser().*
      )
  }

}
