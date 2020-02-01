package db

import javax.inject.{Inject, Singleton}
import io.flow.dependency.v0.models.{Token, TokenForm}
import io.flow.common.v0.models.UserReference
import io.flow.postgresql.{OrderBy, Query}
import io.flow.util.{IdGenerator, Random}
import anorm._
import com.google.inject.Provider
import play.api.db._

sealed trait InternalTokenForm {

  def userId: String
  def tag: String
  def token: String
  def description: Option[String]

}

object InternalTokenForm {

  val GithubOauthTag = "github_oauth"
  val UserCreatedTag = "user_created"

  private[this] val random = Random()
  private[this] val TokenLength = 64

  case class GithubOauth(userId: String, token: String) extends InternalTokenForm {
    override val tag = GithubOauthTag
    override val description = None
  }

  case class UserCreated(form: TokenForm) extends InternalTokenForm {
    override val tag = UserCreatedTag
    override val userId = form.userId
    override val description = form.description
    def token = random.alphaNumeric(TokenLength)
  }

}

@Singleton
class TokensDao @Inject()(
  db: Database,
  usersDaoProvider: Provider[UsersDao]
){

  private[this] val dbHelpers = DbHelpers(db, "tokens")

  private[this] val BaseQuery = Query(s"""
    select tokens.id,
           tokens.user_id,
           tokens.tag,
           substr(token, 1, 3) || '-masked-xxx' as masked,
           null as cleartext,
           tokens.description
      from tokens
  """)

  private[this] val SelectCleartextTokenQuery = Query(s"""
    select token, number_views from tokens
  """)

  private[this] val InsertQuery = """
    insert into tokens
    (id, user_id, tag, token, description, updated_by_user_id)
    values
    ({id}, {user_id}, {tag}, {token}, {description}, {updated_by_user_id})
  """

  private[this] val IncrementNumberViewsQuery = """
    update tokens set number_views = number_views + 1, updated_by_user_id = {updated_by_user_id} where id = {id}
  """

  def setLatestByTag(createdBy: UserReference, form: InternalTokenForm): Token = {
    findAll(Authorization.All, userId = Some(form.userId), tag = Some(form.tag), limit = 1).headOption match {
      case None => {
        create(createdBy, form) match {
          case Left(errors) => sys.error("Failed to create token: " + errors.mkString(", "))
          case Right(token) => token
        }
      }
      case Some(existing) => {
        db.withTransaction { implicit c =>
          dbHelpers.delete(c, createdBy.id, existing.id)
          createWithConnection(createdBy, form) match {
            case Left(errors) => sys.error("Failed to create token: " + errors.mkString(", "))
            case Right(token) => token
          }
        }
      }
    }
  }

  def create(createdBy: UserReference, form: InternalTokenForm): Either[Seq[String], Token] = {
    db.withConnection { implicit c =>
      createWithConnection(createdBy, form)
    }
  }

  private[db] def validate(
    form: InternalTokenForm
  ): Seq[String] = {
    form match {
      case InternalTokenForm.GithubOauth(_, _) => Nil
      case InternalTokenForm.UserCreated(f) => {
        usersDaoProvider.get.findById(f.userId) match {
          case None => Seq("User not found")
          case Some(_) => Nil
        }
      }
    }
  }

  private[this] def createWithConnection(createdBy: UserReference, form: InternalTokenForm)(implicit c: java.sql.Connection): Either[Seq[String], Token] = {
    validate(form) match {
      case Nil => {
        val id = IdGenerator("tok").randomId()

        SQL(InsertQuery).on(
          Symbol("id") -> id,
          Symbol("user_id") -> form.userId,
          Symbol("tag") -> form.tag.trim,
          Symbol("token") -> form.token.trim,
          Symbol("description") -> Util.trimmedString(form.description),
          Symbol("updated_by_user_id") -> createdBy.id
        ).execute()

        Right(
          findAllWithConnection(Authorization.All, id = Some(id), limit = 1).headOption.getOrElse {
            sys.error("Failed to create token")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def addCleartextIfAvailable(user: UserReference, token: Token): Token = {
    db.withConnection { implicit c =>
      SelectCleartextTokenQuery.equals("tokens.id", Some(token.id)).as(
        cleartextTokenParser().*
      ).headOption match {
        case None => token
        case Some(clear) => {
          clear.numberViews match {
            case 0 => {
              incrementNumberViews(user, token.id)(c)
              token.copy(cleartext = Some(clear.token))
            }
            case _ => {
              token
            }
          }
        }
      }
    }
  }

  private[this] def incrementNumberViews(createdBy: UserReference, tokenId: String)(
    implicit c: java.sql.Connection
  ): Unit = {
    SQL(IncrementNumberViewsQuery).on(
      Symbol("id") -> tokenId,
      Symbol("updated_by_user_id") -> createdBy.id
    ).execute()
    ()
  }

  def delete(deletedBy: UserReference, token: Token): Unit = {
    dbHelpers.delete(deletedBy.id, token.id)
  }

  def getCleartextGithubOauthTokenByUserId(
    userId: String
  ): Option[String] = {
    db.withConnection { implicit c =>
      SelectCleartextTokenQuery.
        equals("tokens.user_id", Some(userId)).
        optionalText("tokens.tag", Some(InternalTokenForm.GithubOauthTag)).
        limit(1).
        orderBy("tokens.created_at desc").
        as(
          cleartextTokenParser().*
        ).headOption.map(_.token)
    }
  }

  private[this] case class CleartextToken(token: String, numberViews: Long)

  private[this] def cleartextTokenParser() = {
    SqlParser.str("token") ~ SqlParser.long("number_views") map { case token ~ numberViews => CleartextToken(token, numberViews) }
  }

  def findById(auth: Authorization, id: String): Option[Token] = {
    findAll(auth, id = Some(id), limit = 1).headOption
  }

  def findByToken(token: String): Option[Token] = {
    findAll(Authorization.All, token = Some(token), limit = 1).headOption
  }

  def findAll(
    auth: Authorization,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    token: Option[String] = None,
    userId: Option[String] = None,
    tag: Option[String] = None,
    orderBy: OrderBy = OrderBy("tokens.created_at"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[Token] = {
    db.withConnection { implicit c =>
      findAllWithConnection(
        auth,
        id = id,
        ids = ids,
        token = token,
        userId = userId,
        tag = tag,
        limit = limit,
        offset = offset,
        orderBy = orderBy
      )
    }
  }

  private[this] def findAllWithConnection(
    auth: Authorization,
    id: Option[String],
    ids: Option[Seq[String]] = None,
    token: Option[String] = None,
    userId: Option[String] = None,
    tag: Option[String] = None,
    orderBy: OrderBy = OrderBy("tokens.created_at"),
    limit: Long,
    offset: Long = 0
  )(implicit c: java.sql.Connection): Seq[Token] = {
    Standards.query(
      BaseQuery,
      tableName = "tokens",
      auth = auth.users("tokens.user_id"),
      id = id,
      ids = ids,
      orderBy = orderBy.sql,
      limit = limit,
      offset = offset
    ).
      equals("tokens.user_id", userId).
      equals("tokens.token", token).
      optionalText("tokens.tag", tag, valueFunctions = Seq(Query.Function.Lower, Query.Function.Trim)).
      orderBy(orderBy.sql).
      as(
        io.flow.dependency.v0.anorm.parsers.Token.parser().*
      )
  }

}
