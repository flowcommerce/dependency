package db

import com.bryzek.dependency.v0.models.{Project, Recommendation, RecommendationType}
import io.flow.common.v0.models.UserReference
import io.flow.postgresql.{Query, OrderBy, Pager}
import anorm._
import play.api.db._
import play.api.Play.current
import play.api.libs.json._
import scala.util.{Failure, Success, Try}

object RecommendationsDao {

  private[this] case class RecommendationForm(
    projectId: String,
    `type`: RecommendationType,
    objectId: String,
    name: String,
    from: String,
    to: String
  )

  private[this] val BaseQuery = Query(s"""
    select recommendations.id,
           recommendations.type,
           recommendations.object_id as object_id,
           recommendations.created_at,
           recommendations.name,
           recommendations.from_version as "recommendations.from",
           recommendations.to_version as "recommendations.to",
           projects.id as project_id,
           projects.name as project_name,
           organizations.id as project_organization_id,
           organizations.key as project_organization_key
      from recommendations
      join projects on
             projects.id = recommendations.project_id
      join organizations on
             organizations.id = projects.organization_id
  """)

  private[this] val InsertQuery = """
    insert into recommendations
    (id, project_id, type, object_id, name, from_version, to_version, updated_by_user_id)
    values
    ({id}, {project_id}, {type}, {object_id}, {name}, {from_version}, {to_version}, {updated_by_user_id})
  """

  def sync(user: UserReference, project: Project) {
    val libraries = LibraryRecommendationsDao.forProject(project).map { rec =>
      RecommendationForm(
        projectId = project.id,
        `type` = RecommendationType.Library,
        objectId = rec.library.id,
        name = Seq(rec.library.groupId, rec.library.artifactId).mkString("."),
        from = rec.from,
        to = rec.to.version
      )
    }

    val binaries = BinaryRecommendationsDao.forProject(project).map { rec =>
      RecommendationForm(
        projectId = project.id,
        `type` = RecommendationType.Binary,
        objectId = rec.binary.id,
        name = rec.binary.name.toString,
        from = rec.from,
        to = rec.to.version
      )
    }

    val newRecords = libraries ++ binaries

    val existing = Pager.create { offset =>
      RecommendationsDao.findAll(Authorization.All, projectId = Some(project.id), limit = 1000, offset = offset)
    }.toSeq

    val toAdd = newRecords.filter { rec => !existing.map(toForm(_)).contains(rec) }
    val toRemove = existing.filter { rec => !newRecords.contains(toForm(rec)) }

    DB.withTransaction { implicit c =>
      toAdd.foreach { upsert(user, _) }
      toRemove.foreach { rec =>
        DbHelpers.delete(c, "recommendations", user.id, rec.id)
      }
    }

    if (!toAdd.isEmpty) {
      // TODO: raise event that we found stuff for this project to
      // enable things like notifications.
    }
  }

  def delete(deletedBy: UserReference, rec: Recommendation) {
    DbHelpers.delete("recommendations", deletedBy.id, rec.id)
  }

  private[this] def upsert(
    createdBy: UserReference,
    form: RecommendationForm
  ) (
    implicit c: java.sql.Connection
  ) {
    findByProjectIdAndTypeAndObjectIdAndNameAndFromVersion(
      Authorization.All,
      form.projectId,
      form.`type`,
      form.objectId,
      form.name,
      form.from
    ) match {
      case None => {
        Try(create(createdBy, form)) match {
          case Success(rec) => rec
          case Failure(ex) => {
            throw ex
          }
        }
      }
      case Some(rec) => {
        (rec.to == form.to) match {
          case true => {
            // No-op
          }
          case false => {
            DbHelpers.delete(c, "recommendations", createdBy.id, rec.id)
            create(createdBy, form)
          }
        }
      }
    }
  }

  private[this] def create(
    createdBy: UserReference,
    form: RecommendationForm
  ) (
    implicit c: java.sql.Connection
  ) {
    val id = io.flow.play.util.IdGenerator("rec").randomId()
    SQL(InsertQuery).on(
      'id -> id,
      'project_id -> form.projectId,
      'type -> form.`type`.toString,
      'object_id -> form.objectId,
      'name -> form.name,
      'from_version -> form.from,
      'to_version -> form.to,
      'updated_by_user_id -> createdBy.id
    ).execute()
  }

  def findByProjectIdAndTypeAndObjectIdAndNameAndFromVersion(
    auth: Authorization,
    projectId: String,
    `type`: RecommendationType,
    objectId: String,
    name: String,
    fromVersion: String
  ): Option[Recommendation] = {
    findAll(
      auth,
      projectId = Some(projectId),
      `type` = Some(`type`),
      objectId = Some(objectId),
      name = Some(name),
      fromVersion = Some(fromVersion)
    ).headOption
  }

  def findById(auth: Authorization, id: String): Option[Recommendation] = {
    findAll(auth, id = Some(id), limit = 1).headOption
  }

  def findAll(
    auth: Authorization,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    organization: Option[String] = None,
    projectId: Option[String] = None,
    `type`: Option[RecommendationType] = None,
    objectId: Option[String] = None,
    name: Option[String] = None,
    fromVersion: Option[String] = None,
    orderBy: OrderBy = OrderBy("-recommendations.created_at, lower(projects.name), lower(recommendations.name)"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[Recommendation] = {
    DB.withConnection { implicit c =>
      Standards.query(
        BaseQuery,
        tableName = "recommendations",
        auth = auth.organizations("projects.organization_id", Some("projects.visibility")),
        id = id,
        ids = ids,
        orderBy = orderBy.sql,
        limit = limit,
        offset = offset
      ).
        and(
          organization.map { org =>
            "organizations.id in (select id from organizations where key = lower(trim({org})))"
          }
        ).bind("org", organization).
        equals("recommendations.project_id", projectId).
        optionalText(
          "recommendations.type",
          `type`,
          valueFunctions = Seq(Query.Function.Lower, Query.Function.Trim)
        ).
        optionalText("recommendations.name", name).
        optionalText("recommendations.from_version", fromVersion).
        equals("recommendations.object_id", objectId).
        as(
          com.bryzek.dependency.v0.anorm.parsers.Recommendation.parser().*
        )
    }
  }

  private[this] def toForm(rec: Recommendation): RecommendationForm = RecommendationForm(
    projectId = rec.project.id,
    `type` = rec.`type`,
    objectId = rec.`object`.id,
    name = rec.name,
    from = rec.from,
    to = rec.to
  )

}
