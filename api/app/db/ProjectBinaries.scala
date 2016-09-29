package db

import com.bryzek.dependency.actors.MainActor
import com.bryzek.dependency.api.lib.Version
import com.bryzek.dependency.v0.models.{Binary, BinaryType, Project, ProjectBinary, SyncEvent}
import io.flow.postgresql.{Query, OrderBy, Pager}
import io.flow.common.v0.models.UserReference
import anorm._
import play.api.db._
import play.api.Play.current
import play.api.libs.json._

case class ProjectBinaryForm(
  projectId: String,
  name: BinaryType,
  version: String,
  path: String
)

object ProjectBinariesDao {

  private[this] val BaseQuery = Query(s"""
    select project_binaries.id,
           project_binaries.name,
           project_binaries.version,
           project_binaries.path,
           project_binaries.binary_id as binary_id,
           projects.id as project_id,
           projects.name as project_name,
           organizations.id as project_organization_id,
           organizations.key as project_organization_key
      from project_binaries
      join projects on projects.id = project_binaries.project_id
      join organizations on organizations.id = projects.organization_id
  """)

  private[this] val InsertQuery = """
    insert into project_binaries
    (id, project_id, name, version, path, updated_by_user_id)
    values
    ({id}, {project_id}, {name}, {version}, {path}, {updated_by_user_id})
  """

  private[this] val RemoveBinaryQuery = """
    update project_binaries
       set binary_id = null,
           updated_by_user_id = {updated_by_user_id}
     where id = {id}
  """

  private[this] val SetBinaryQuery = """
    update project_binaries
       set binary_id = {binary_id},
           updated_by_user_id = {updated_by_user_id}
     where id = {id}
  """

  private[db] def validate(
    user: UserReference,
    form: ProjectBinaryForm
  ): Seq[String] = {
    val nameErrors = if (form.name.toString.trim.isEmpty) {
      Seq("Name cannot be empty")
    } else {
      Nil
    }

    val versionErrors = if (form.version.trim.isEmpty) {
      Seq("Version cannot be empty")
    } else {
      Nil
    }

    val projectErrors = ProjectsDao.findById(Authorization.All, form.projectId) match {
      case None => Seq("Project not found")
      case Some(project) => {
        MembershipsDao.isMemberByOrgId(project.organization.id, user) match {
          case false => Seq("You are not authorized to edit this project")
          case true => Nil
        }
      }
    }

    val existsErrors = if (nameErrors.isEmpty && versionErrors.isEmpty) {
      ProjectBinariesDao.findByProjectIdAndNameAndVersion(
        Authorization.All, form.projectId, form.name.toString, form.version
      ) match {
        case None => Nil
        case Some(lib) => {
          Seq("Project binary with this name and version already exists")
        }
      }
    } else {
      Nil
    }

    projectErrors ++ nameErrors ++ versionErrors ++ existsErrors
  }

  def upsert(createdBy: UserReference, form: ProjectBinaryForm): Either[Seq[String], ProjectBinary] = {
    ProjectBinariesDao.findByProjectIdAndNameAndVersion(
      Authorization.All, form.projectId, form.name.toString, form.version
    ) match {
      case None => {
        create(createdBy, form)
      }
      case Some(lib) => {
        Right(lib)
      }
    }
  }

  def create(createdBy: UserReference, form: ProjectBinaryForm): Either[Seq[String], ProjectBinary] = {
    validate(createdBy, form) match {
      case Nil => {
        val id = io.flow.play.util.IdGenerator("prb").randomId()

        DB.withConnection { implicit c =>
          SQL(InsertQuery).on(
            'id -> id,
            'project_id -> form.projectId,
            'name -> form.name.toString.trim,
            'version -> form.version.trim,
            'path -> form.path.trim,
            'updated_by_user_id -> createdBy.id
          ).execute()
          MainActor.ref ! MainActor.Messages.ProjectBinaryCreated(form.projectId, id)
        }

        Right(
          findById(Authorization.All, id).getOrElse {
            sys.error("Failed to create project binary")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def removeBinary(user: UserReference, projectBinary: ProjectBinary) {
    DB.withConnection { implicit c =>
      SQL(RemoveBinaryQuery).on(
        'id -> projectBinary.id,
        'updated_by_user_id -> user.id
      ).execute()
    }
  }

  /**
    * Removes any project binary ids for this project not specified in this list
    */
  def setIds(user: UserReference, projectId: String, projectBinaries: Seq[ProjectBinary]) {
    val ids = projectBinaries.map(_.id)
    Pager.create { offset =>
      findAll(Authorization.All, projectId = Some(projectId), limit = 100, offset = offset)
    }.foreach { projectBinary =>
      if (!ids.contains(projectBinary.id)) {
        delete(user, projectBinary)
      }
    }

  }

  def setBinary(user: UserReference, projectBinary: ProjectBinary, binary: Binary) {
    DB.withConnection { implicit c =>
      SQL(SetBinaryQuery).on(
        'id -> projectBinary.id,
        'binary_id -> binary.id,
        'updated_by_user_id -> user.id
      ).execute()
    }
  }

  def delete(deletedBy: UserReference, binary: ProjectBinary) {
    DbHelpers.delete("project_binaries", deletedBy.id, binary.id)
    MainActor.ref ! MainActor.Messages.ProjectBinaryDeleted(binary.project.id, binary.id, binary.version)
  }

  def findByProjectIdAndNameAndVersion(
    auth: Authorization,
    projectId: String,
    name: String,
    version: String
  ): Option[ProjectBinary] = {
    findAll(
      auth,
      projectId = Some(projectId),
      name = Some(name),
      version = Some(version),
      limit = 1
    ).headOption
  }

  def findById(auth: Authorization, id: String): Option[ProjectBinary] = {
    findAll(auth, id = Some(id), limit = 1).headOption
  }

  def findAll(
    auth: Authorization,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    projectId: Option[String] = None,
    binaryId: Option[String] = None,
    name: Option[String] = None,
    version: Option[String] = None,
    isSynced: Option[Boolean] = None,
    hasBinary: Option[Boolean] = None,
    orderBy: OrderBy = OrderBy("lower(project_binaries.name), project_binaries.created_at"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[ProjectBinary] = {
    DB.withConnection { implicit c =>
      Standards.query(
        BaseQuery,
        tableName = "project_binaries",
        auth = auth.organizations("organizations.id", Some("projects.visibility")),
        id = id,
        ids = ids,
        orderBy = orderBy.sql,
        limit = limit,
        offset = offset
      ).
        equals("project_binaries.project_id", projectId).
        equals("project_binaries.binary_id", binaryId).
        optionalText(
          "project_binaries.name",
          name,
          columnFunctions = Seq(Query.Function.Lower),
          valueFunctions = Seq(Query.Function.Lower, Query.Function.Trim)
        ).
        optionalText(
          "project_binaries.version",
          version
        ).
        and(
          isSynced.map { value =>
            val clause = "select 1 from syncs where object_id = project_binaries.id and event = {sync_event_completed}"
            value match {
              case true => s"exists ($clause)"
              case false => s"not exists ($clause)"
            }
          }
        ).
        bind("sync_event_completed", isSynced.map(_ => SyncEvent.Completed.toString)).
        nullBoolean("project_binaries.binary_id", hasBinary).
        as(
          com.bryzek.dependency.v0.anorm.parsers.ProjectBinary.parser().*
        )
    }
  }

}
