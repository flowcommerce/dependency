package db

import javax.inject.{Inject, Singleton}

import anorm._
import com.google.inject.Provider
import io.flow.common.v0.models.{User, UserReference}
import io.flow.dependency.v0.models.{Organization, OrganizationForm, Role}
import io.flow.play.util.UrlKey
import io.flow.util.{IdGenerator, Random}
import io.flow.postgresql.{OrderBy, Pager, Query}
import play.api.db._

@Singleton
class OrganizationsDao @Inject() (
  db: Database,
  projectsDaoProvider: Provider[ProjectsDao],
  membershipsDaoProvider: Provider[MembershipsDao],
) {

  val DefaultUserNameLength = 8

  private[this] val dbHelpers = DbHelpers(db, "organizations")

  private[this] val BaseQuery = Query(s"""
    select organizations.id,
           organizations.user_id,
           organizations.key
      from organizations
  """)

  private[this] val InsertQuery = """
    insert into organizations
    (id, user_id, key, updated_by_user_id)
    values
    ({id}, {user_id}, {key}, {updated_by_user_id})
  """

  private[this] val UpdateQuery = """
    update organizations
       set key = {key},
           updated_by_user_id = {updated_by_user_id}
     where id = {id}
  """

  private[this] val InsertUserOrganizationQuery = """
    insert into user_organizations
    (id, user_id, organization_id, updated_by_user_id)
    values
    ({id}, {user_id}, {organization_id}, {updated_by_user_id})
  """

  private[this] val random = Random()
  private[this] val urlKey = UrlKey(minKeyLength = 3)

  private[db] def validate(
    form: OrganizationForm,
    existing: Option[Organization] = None,
  ): Seq[String] = {
    if (form.key.trim == "") {
      Seq("Key cannot be empty")

    } else {
      urlKey.validate(form.key.trim) match {
        case Nil => {
          findByKey(Authorization.All, form.key) match {
            case None => Seq.empty
            case Some(p) => {
              existing.map(_.id).contains(p.id) match {
                case true => Nil
                case false => Seq("Organization with this key already exists")
              }
            }
          }
        }
        case errors => errors
      }
    }
  }

  def create(createdBy: UserReference, form: OrganizationForm): Either[Seq[String], Organization] = {
    validate(form) match {
      case Nil => {
        val id = db.withTransaction { implicit c =>
          create(c, createdBy, form)
        }
        Right(
          findById(Authorization.All, id).getOrElse {
            sys.error("Failed to create organization")
          },
        )
      }
      case errors => Left(errors)
    }
  }

  private[this] def create(implicit
    c: java.sql.Connection,
    createdBy: UserReference,
    form: OrganizationForm,
  ): String = {
    val id = IdGenerator("org").randomId()

    SQL(InsertQuery)
      .on(
        "id" -> id,
        "user_id" -> createdBy.id,
        "key" -> form.key.trim,
        "updated_by_user_id" -> createdBy.id,
      )
      .execute()

    membershipsDaoProvider.get.create(
      c,
      createdBy,
      id,
      createdBy.id,
      Role.Admin,
    )

    id
  }

  def update(
    createdBy: UserReference,
    organization: Organization,
    form: OrganizationForm,
  ): Either[Seq[String], Organization] = {
    validate(form, Some(organization)) match {
      case Nil => {
        db.withConnection { implicit c =>
          SQL(UpdateQuery)
            .on(
              "id" -> organization.id,
              "key" -> form.key.trim,
              "updated_by_user_id" -> createdBy.id,
            )
            .execute()
        }

        Right(
          findById(Authorization.All, organization.id).getOrElse {
            sys.error("Failed to create organization")
          },
        )
      }
      case errors => Left(errors)
    }
  }

  def delete(deletedBy: UserReference, organization: Organization): Either[Seq[String], Unit] = {
    membershipsDaoProvider.get.authorizeOrgId(organization.id, deletedBy) {
      Pager
        .create { offset =>
          projectsDaoProvider.get.findAll(
            Authorization.All,
            organizationId = Some(organization.id),
            limit = None,
            offset = offset,
          )
        }
        .foreach { project =>
          projectsDaoProvider.get.delete(deletedBy, project)
        }

      Pager
        .create { offset =>
          membershipsDaoProvider.get.findAll(Authorization.All, organizationId = Some(organization.id), offset = offset)
        }
        .foreach { membership =>
          membershipsDaoProvider.get.delete(deletedBy, membership)
        }

      dbHelpers.delete(deletedBy.id, organization.id)
    }
  }

  def upsertForUser(user: User): Organization = {
    findAll(Authorization.All, forUserId = Some(user.id), limit = 1).headOption.getOrElse {
      val key = urlKey.generate(defaultUserName(user))

      val orgId = db.withTransaction { implicit c =>
        val orgId = create(
          c,
          UserReference(id = user.id),
          OrganizationForm(
            key = key,
          ),
        )

        SQL(InsertUserOrganizationQuery)
          .on(
            "id" -> IdGenerator("uso").randomId(),
            "user_id" -> user.id,
            "organization_id" -> orgId,
            "updated_by_user_id" -> user.id,
          )
          .execute()

        orgId
      }
      findById(Authorization.All, orgId).getOrElse {
        sys.error(s"Failed to create an organization for the user[$user]")
      }
    }
  }

  /** Generates a default username for this user based on email or name.
    */
  def defaultUserName(user: User): String = {
    urlKey.format(
      user.email match {
        case Some(email) => {
          email.substring(0, email.indexOf("@"))
        }
        case None => {
          (user.name.first, user.name.last) match {
            case (None, None) => random.alphaNumeric(DefaultUserNameLength)
            case (Some(first), None) => first
            case (None, Some(last)) => last
            case (Some(first), Some(last)) => s"${first(0)}$last"
          }
        }
      },
    )
  }

  def findByKey(auth: Authorization, key: String): Option[Organization] = {
    findAll(auth, key = Some(key), limit = 1).headOption
  }

  def findById(auth: Authorization, id: String): Option[Organization] = {
    findAll(auth, id = Some(id), limit = 1).headOption
  }

  def findAll(
    auth: Authorization,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    userId: Option[String] = None,
    key: Option[String] = None,
    forUserId: Option[String] = None,
    orderBy: OrderBy = OrderBy("organizations.key, -organizations.created_at"),
    limit: Long = 25,
    offset: Long = 0,
  ): Seq[Organization] = {
    db.withConnection { implicit c =>
      Standards
        .query(
          BaseQuery,
          tableName = "organizations",
          auth = auth.organizations("organizations.id"),
          id = id,
          ids = ids,
          orderBy = orderBy.sql,
          limit = limit,
          offset = offset,
        )
        .and(
          userId.map { _ =>
            "organizations.id in (select organization_id from memberships where user_id = {user_id})"
          },
        )
        .bind("user_id", userId)
        .optionalText(
          "organizations.key",
          key,
          columnFunctions = Seq(Query.Function.Lower),
          valueFunctions = Seq(Query.Function.Lower, Query.Function.Trim),
        )
        .and(
          forUserId.map { _ =>
            "organizations.id in (select organization_id from user_organizations where user_id = {for_user_id})"
          },
        )
        .bind("for_user_id", forUserId)
        .as(
          io.flow.dependency.v0.anorm.parsers.Organization.parser().*,
        )
    }
  }

}
