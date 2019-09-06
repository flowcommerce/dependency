package db

import javax.inject.Inject
import io.flow.dependency.actors.MainActor
import io.flow.dependency.api.lib.Validation
import io.flow.dependency.v0.models.{Credentials, Resolver, ResolverForm, ResolverSummary}
import io.flow.dependency.v0.models.{OrganizationSummary, Visibility}
import io.flow.dependency.v0.models.json._
import io.flow.common.v0.models.UserReference
import io.flow.postgresql.{Pager, Query}
import io.flow.log.RollbarLogger
import io.flow.util.IdGenerator
import anorm._
import com.google.inject.Provider
import play.api.db._
import play.api.libs.json._

class ResolversDao @Inject()(
  db: Database,
  membershipsDaoProvider: Provider[MembershipsDao],
  librariesDaoProvider: Provider[LibrariesDao],
  organizationsDaoProvider: Provider[OrganizationsDao],
  usersDaoProvider: Provider[UsersDao],
  logger: RollbarLogger,
  @javax.inject.Named("main-actor") mainActor: akka.actor.ActorRef
) {

  val GithubOauthResolverTag = "github_oauth"

  private[this] val dbHelpers = DbHelpers(db, "resolvers")

  private[this] val BaseQuery = Query(s"""
    select resolvers.id,
           resolvers.visibility,
           resolvers.credentials::varchar,
           resolvers.uri,
           resolvers.position,
           organizations.id as organization_id,
           organizations.key as organization_key
      from resolvers
      left join organizations on organizations.id = resolvers.organization_id
  """)

  private[this] val SelectCredentialsQuery = s"""
    select credentials::varchar from resolvers where id = {id}
  """

  private[this] val InsertQuery = """
    insert into resolvers
    (id, visibility, credentials, position, organization_id, uri, updated_by_user_id)
    values
    ({id}, {visibility}, {credentials}::json, {position}, {organization_id}, {uri}, {updated_by_user_id})
  """

  def credentials(resolver: Resolver): Option[Credentials] = {
    resolver.credentials.flatMap { _ =>
      db.withConnection { implicit c =>
        SQL(SelectCredentialsQuery).on('id -> resolver.id).as(
          SqlParser.str("credentials").*
        ).headOption.flatMap { parseCredentials(resolver.id, _) }
      }
    }
  }

  def toSummary(resolver: Resolver): ResolverSummary = {
    ResolverSummary(
      id = resolver.id,
      organization = resolver.organization.map { org =>
        OrganizationSummary(org.id, org.key)
      },
      visibility = resolver.visibility,
      uri = resolver.uri
    )
  }

  def validate(user: UserReference, form: ResolverForm): Seq[String] = {
    val urlErrors = Validation.validateUri(form.uri) match {
      case Left(errors) => errors
      case Right(_) => Nil
    }

    val uniqueErrors = form.visibility match {
      case Visibility.Public | Visibility.UNDEFINED(_) => {
        findAll(
          Authorization.All,
          visibility = Some(Visibility.Public),
          uri = Some(form.uri),
          limit = 1
        ).headOption match {
          case None => Nil
          case Some(_) => Seq(s"Public resolver with uri[${form.uri}] already exists")
        }
      }
      case Visibility.Private => {
        findAll(
          Authorization.All,
          visibility = Some(Visibility.Private),
          organization = Some(form.organization),
          uri = Some(form.uri),
          limit = 1
        ).headOption match {
          case None => Nil
          case Some(_) => Seq(s"Organization already has a resolver with uri[${form.uri}]")
        }
      }
    }

    val organizationErrors = if (membershipsDaoProvider.get.isMemberByOrgKey(form.organization, user)) {
      Nil
    } else {
      Seq("You do not have access to this organization")
    }

    urlErrors ++ uniqueErrors ++ organizationErrors
  }

  def upsert(createdBy: UserReference, form: ResolverForm): Either[Seq[String], Resolver] = {
    findByOrganizationAndUri(Authorization.All, form.organization, form.uri) match {
      case Some(resolver) => Right(resolver)
      case None => create(createdBy, form)
    }
  }

  def create(createdBy: UserReference, form: ResolverForm): Either[Seq[String], Resolver] = {
    validate(createdBy, form) match {
      case Nil => {
        val org = organizationsDaoProvider.get.findByKey(Authorization.All, form.organization).getOrElse {
          sys.error(s"Could not find organization with key[${form.organization}]")
        }

        val id = IdGenerator("res").randomId()

        db.withConnection { implicit c =>
          SQL(InsertQuery).on(
            'id -> id,
            'organization_id -> org.id,
            'visibility -> form.visibility.toString,
            'credentials -> form.credentials.map { cred => Json.stringify(Json.toJson(cred)) },
            'position -> nextPosition(org.id, form.visibility),
            'uri -> form.uri.trim,
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        mainActor ! MainActor.Messages.ResolverCreated(id)

        Right(
          findById(Authorization.All, id).getOrElse {
            sys.error("Failed to create resolver")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def delete(deletedBy: UserReference, resolver: Resolver): Unit = {
    Pager.create { offset =>
      librariesDaoProvider.get.findAll(
        Authorization.All,
        resolverId = Some(resolver.id),
        offset = offset
      )
    }.foreach { library =>
      librariesDaoProvider.get.delete(usersDaoProvider.get.systemUser, library)
    }

    mainActor ! MainActor.Messages.ResolverDeleted(resolver.id)
    dbHelpers.delete(deletedBy.id, resolver.id)
  }

  def findByOrganizationAndUri(
    auth: Authorization,
    organization: String,
    uri: String
  ): Option[Resolver] = {
    findAll(
      auth,
      organization = Some(organization),
      uri = Some(uri),
      limit = 1
    ).headOption
  }

  def findById(auth: Authorization, id: String): Option[Resolver] = {
    findAll(auth, id = Some(id), limit = 1).headOption
  }

  def findAll(
    auth: Authorization,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    visibility: Option[Visibility] = None,
    organization: Option[String] = None,
    organizationId: Option[String] = None,
    uri: Option[String] = None,
    limit: Long = 25,
    offset: Long = 0
  ): Seq[Resolver] = {
    db.withConnection { implicit c =>
      Standards.query(
        BaseQuery,
        tableName = "resolvers",
        auth = auth.organizations("resolvers.organization_id", Some("resolvers.visibility")),
        id = id,
        ids = ids,
        orderBy = Some(s"""
          case when visibility = '${Visibility.Public}' then 0
               when visibility = '${Visibility.Private}' then 1
               else 2 end,
          resolvers.position, lower(resolvers.uri),resolvers.created_at
        """),
        limit = limit,
        offset = offset
      ).
        optionalText("resolvers.visibility", visibility).
        optionalText("organizations.key", organization.map(_.toLowerCase)).
        equals("organizations.id", organizationId).
        optionalText("resolvers.uri", uri).
        as(
          parser().*
        )
    }
  }

  private[this] val NextPublicPositionQuery = """
    select coalesce(max(position) + 1, 0) as position
      from resolvers
     where visibility = 'public'
  """

  private[this] val NextPrivatePositionQuery = """
    select coalesce(max(position) + 1, 0) as position
      from resolvers
     where visibility = 'private'
       and organization_id = {organization_id}
  """

  /**
    * Returns the next free position
    */
  def nextPosition(
    organizationId: String,
    visibility: Visibility
  ): Int = {
    db.withConnection { implicit c =>
      visibility match {
        case Visibility.Public => {
          SQL(NextPublicPositionQuery).as(SqlParser.int("position").single)
        }
        case  Visibility.Private | Visibility.UNDEFINED(_) => {
          SQL(NextPrivatePositionQuery).on("organization_id" -> organizationId.toString).as(SqlParser.int("position").single)
        }
      }
    }
  }

  private[this] def parser(): RowParser[io.flow.dependency.v0.models.Resolver] = {
    SqlParser.str("id") ~
    io.flow.dependency.v0.anorm.parsers.Visibility.parser("visibility") ~
    io.flow.dependency.v0.anorm.parsers.OrganizationSummary.parserWithPrefix("organization").? ~
    SqlParser.str("uri") ~
    SqlParser.str("credentials").? map {
      case id ~ visibility ~ organization ~ uri ~ credentials => {
        io.flow.dependency.v0.models.Resolver(
          id = id,
          visibility = visibility,
          organization = organization,
          uri = uri,
          credentials = credentials.flatMap { parseCredentials(id, _) }.flatMap(Util.maskCredentials)
        )
      }
    }
  }

  private[this] def parseCredentials(resolverId: String, value: String): Option[Credentials] = {
    Json.parse(value).validate[Credentials] match {
      case JsSuccess(credentials, _) => {
        Some(credentials)
      }
      case JsError(error) => {
        logger.withKeyValue("resolver", resolverId).withKeyValue("error", error.toString()).warn(s"Resolver has credentials that could not be parsed")
        None
      }
    }
  }

}
