package db.generated

import anorm._
import io.flow.common.v0.models.UserReference
import io.flow.postgresql.{OrderBy, Query}
import io.flow.util.IdGenerator
import java.sql.Connection
import javax.inject.{Inject, Singleton}
import play.api.db.Database

case class ProjectLibrary(
  id: String,
  organizationId: String,
  projectId: String,
  groupId: String,
  artifactId: String,
  version: String,
  crossBuildVersion: Option[String],
  path: String,
  libraryId: Option[String]
) {

  lazy val form: ProjectLibraryForm = ProjectLibraryForm(
    organizationId = organizationId,
    projectId = projectId,
    groupId = groupId,
    artifactId = artifactId,
    version = version,
    crossBuildVersion = crossBuildVersion,
    path = path,
    libraryId = libraryId
  )

}

case class ProjectLibraryForm(
  organizationId: String,
  projectId: String,
  groupId: String,
  artifactId: String,
  version: String,
  crossBuildVersion: Option[String],
  path: String,
  libraryId: Option[String]
)

object ProjectLibrariesTable {
  val Schema: String = "public"
  val Name: String = "project_libraries"

  object Columns {
    val Id: String = "id"
    val OrganizationId: String = "organization_id"
    val ProjectId: String = "project_id"
    val GroupId: String = "group_id"
    val ArtifactId: String = "artifact_id"
    val Version: String = "version"
    val CrossBuildVersion: String = "cross_build_version"
    val Path: String = "path"
    val LibraryId: String = "library_id"
    val CreatedAt: String = "created_at"
    val UpdatedAt: String = "updated_at"
    val UpdatedByUserId: String = "updated_by_user_id"
    val HashCode: String = "hash_code"
    val all: List[String] = List(Id, OrganizationId, ProjectId, GroupId, ArtifactId, Version, CrossBuildVersion, Path, LibraryId, CreatedAt, UpdatedAt, UpdatedByUserId, HashCode)
  }
}

@Singleton
class ProjectLibrariesDao @Inject() (
  val db: Database
) {

  private[this] val idGenerator = IdGenerator("prl")

  def randomId(): String = idGenerator.randomId()

  private[this] val BaseQuery = Query("""
      | select project_libraries.id,
      |        project_libraries.organization_id,
      |        project_libraries.project_id,
      |        project_libraries.group_id,
      |        project_libraries.artifact_id,
      |        project_libraries.version,
      |        project_libraries.cross_build_version,
      |        project_libraries.path,
      |        project_libraries.library_id,
      |        project_libraries.created_at,
      |        project_libraries.updated_at,
      |        project_libraries.updated_by_user_id,
      |        project_libraries.hash_code
      |   from project_libraries
  """.stripMargin)

  private[this] val InsertQuery = Query("""
    | insert into project_libraries
    | (id, organization_id, project_id, group_id, artifact_id, version, cross_build_version, path, library_id, updated_by_user_id, hash_code)
    | values
    | ({id}, {organization_id}, {project_id}, {group_id}, {artifact_id}, {version}, {cross_build_version}, {path}, {library_id}, {updated_by_user_id}, {hash_code}::bigint)
  """.stripMargin)

  private[this] val UpdateQuery = Query("""
    | update project_libraries
    |    set organization_id = {organization_id},
    |        project_id = {project_id},
    |        group_id = {group_id},
    |        artifact_id = {artifact_id},
    |        version = {version},
    |        cross_build_version = {cross_build_version},
    |        path = {path},
    |        library_id = {library_id},
    |        updated_by_user_id = {updated_by_user_id},
    |        hash_code = {hash_code}::bigint
    |  where id = {id}
    |    and project_libraries.hash_code != {hash_code}::bigint
  """.stripMargin)

  private[this] def bindQuery(query: Query, form: ProjectLibraryForm): Query = {
    query.
      bind("organization_id", form.organizationId).
      bind("project_id", form.projectId).
      bind("group_id", form.groupId).
      bind("artifact_id", form.artifactId).
      bind("version", form.version).
      bind("cross_build_version", form.crossBuildVersion).
      bind("path", form.path).
      bind("library_id", form.libraryId).
      bind("hash_code", form.hashCode())
  }

  def insert(updatedBy: UserReference, form: ProjectLibraryForm): String = {
    db.withConnection { c =>
      insert(c, updatedBy, form)
    }
  }

  def insert(c: Connection, updatedBy: UserReference, form: ProjectLibraryForm): String = {
    val id = randomId()
    bindQuery(InsertQuery, form).
      bind("id", id).
      bind("updated_by_user_id", updatedBy.id).
      anormSql.execute()(c)
    id
  }

  def updateIfChangedById(updatedBy: UserReference, id: String, form: ProjectLibraryForm): Unit = {
    if (!findById(id).map(_.form).contains(form)) {
      updateById(updatedBy, id, form)
    }
  }

  def updateById(updatedBy: UserReference, id: String, form: ProjectLibraryForm): Unit = {
    db.withConnection { c =>
      updateById(c, updatedBy, id, form)
    }
  }

  def updateById(c: Connection, updatedBy: UserReference, id: String, form: ProjectLibraryForm): Unit = {
    bindQuery(UpdateQuery, form).
      bind("id", id).
      bind("updated_by_user_id", updatedBy.id).
      anormSql.execute()(c)
    ()
  }

  def update(updatedBy: UserReference, existing: ProjectLibrary, form: ProjectLibraryForm): Unit = {
    db.withConnection { c =>
      update(c, updatedBy, existing, form)
    }
  }

  def update(c: Connection, updatedBy: UserReference, existing: ProjectLibrary, form: ProjectLibraryForm): Unit = {
    updateById(c, updatedBy, existing.id, form)
  }

  def findById(id: String): Option[ProjectLibrary] = {
    db.withConnection { c =>
      findByIdWithConnection(c, id)
    }
  }

  def findByIdWithConnection(c: java.sql.Connection, id: String): Option[ProjectLibrary] = {
    findAllWithConnection(c, ids = Some(Seq(id)), limit = Some(1L), orderBy = None).headOption
  }

  def iterateAll(
    ids: Option[Seq[String]] = None,
    organizationId: Option[String] = None,
    organizationIds: Option[Seq[String]] = None,
    projectId: Option[String] = None,
    projectIds: Option[Seq[String]] = None,
    artifactId: Option[String] = None,
    artifactIds: Option[Seq[String]] = None,
    groupId: Option[String] = None,
    groupIds: Option[Seq[String]] = None,
    libraryId: Option[String] = None,
    hasLibraryId: Option[Boolean] = None,
    libraryIds: Option[Seq[String]] = None,
    version: Option[String] = None,
    versions: Option[Seq[String]] = None,
    pageSize: Long = 2000L,
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Iterator[ProjectLibrary] = {
    def iterate(lastValue: Option[ProjectLibrary]): Iterator[ProjectLibrary] = {
      val page = findAll(
        ids = ids,
        organizationId = organizationId,
        organizationIds = organizationIds,
        projectId = projectId,
        projectIds = projectIds,
        artifactId = artifactId,
        artifactIds = artifactIds,
        groupId = groupId,
        groupIds = groupIds,
        libraryId = libraryId,
        hasLibraryId = hasLibraryId,
        libraryIds = libraryIds,
        version = version,
        versions = versions,
        limit = Some(pageSize),
        orderBy = Some(OrderBy("project_libraries.id")),
      ) { q => customQueryModifier(q).greaterThan("project_libraries.id", lastValue.map(_.id)) }

      page.lastOption match {
        case None => Iterator.empty
        case lastValue => page.iterator ++ iterate(lastValue)
      }
    }

    iterate(None)
  }

  def findAll(
    ids: Option[Seq[String]] = None,
    organizationId: Option[String] = None,
    organizationIds: Option[Seq[String]] = None,
    projectId: Option[String] = None,
    projectIds: Option[Seq[String]] = None,
    artifactId: Option[String] = None,
    artifactIds: Option[Seq[String]] = None,
    groupId: Option[String] = None,
    groupIds: Option[Seq[String]] = None,
    libraryId: Option[String] = None,
    hasLibraryId: Option[Boolean] = None,
    libraryIds: Option[Seq[String]] = None,
    version: Option[String] = None,
    versions: Option[Seq[String]] = None,
    limit: Option[Long],
    offset: Long = 0,
    orderBy: Option[OrderBy] = Some(OrderBy("project_libraries.id"))
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Seq[ProjectLibrary] = {
    db.withConnection { c =>
      findAllWithConnection(
        c,
        ids = ids,
        organizationId = organizationId,
        organizationIds = organizationIds,
        projectId = projectId,
        projectIds = projectIds,
        artifactId = artifactId,
        artifactIds = artifactIds,
        groupId = groupId,
        groupIds = groupIds,
        libraryId = libraryId,
        hasLibraryId = hasLibraryId,
        libraryIds = libraryIds,
        version = version,
        versions = versions,
        limit = limit,
        offset = offset,
        orderBy = orderBy
      )(customQueryModifier)
    }
  }

  def findAllWithConnection(
    c: java.sql.Connection,
    ids: Option[Seq[String]] = None,
    organizationId: Option[String] = None,
    organizationIds: Option[Seq[String]] = None,
    projectId: Option[String] = None,
    projectIds: Option[Seq[String]] = None,
    artifactId: Option[String] = None,
    artifactIds: Option[Seq[String]] = None,
    groupId: Option[String] = None,
    groupIds: Option[Seq[String]] = None,
    libraryId: Option[String] = None,
    hasLibraryId: Option[Boolean] = None,
    libraryIds: Option[Seq[String]] = None,
    version: Option[String] = None,
    versions: Option[Seq[String]] = None,
    limit: Option[Long],
    offset: Long = 0,
    orderBy: Option[OrderBy] = Some(OrderBy("project_libraries.id"))
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Seq[ProjectLibrary] = {
    customQueryModifier(BaseQuery).
      optionalIn("project_libraries.id", ids).
      equals("project_libraries.organization_id", organizationId).
      optionalIn("project_libraries.organization_id", organizationIds).
      equals("project_libraries.project_id", projectId).
      optionalIn("project_libraries.project_id", projectIds).
      equals("project_libraries.artifact_id", artifactId).
      optionalIn("project_libraries.artifact_id", artifactIds).
      equals("project_libraries.group_id", groupId).
      optionalIn("project_libraries.group_id", groupIds).
      equals("project_libraries.library_id", libraryId).
      nullBoolean("project_libraries.library_id", hasLibraryId).
      optionalIn("project_libraries.library_id", libraryIds).
      equals("project_libraries.version", version).
      optionalIn("project_libraries.version", versions).
      optionalLimit(limit).
      offset(offset).
      orderBy(orderBy.flatMap(_.sql)).
      as(ProjectLibrariesDao.parser.*)(c)
  }

  def delete(deletedBy: UserReference, projectLibrary: ProjectLibrary): Unit = {
    db.withConnection { c =>
      delete(c, deletedBy, projectLibrary)
    }
  }

  def delete(c: Connection, deletedBy: UserReference, projectLibrary: ProjectLibrary): Unit = {
    deleteById(c, deletedBy, projectLibrary.id)
  }

  def deleteAllByArtifactId(deletedBy: UserReference, artifactId: String): Unit = {
    db.withConnection { c =>
      deleteAllByArtifactId(c, deletedBy, artifactId)
    }
  }

  def deleteAllByArtifactId(c: Connection, deletedBy: UserReference, artifactId: String): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from project_libraries")
      .equals("artifact_id", artifactId)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteAllByArtifactIds(deletedBy: UserReference, artifactIds: Seq[String]): Unit = {
    db.withConnection { c =>
      deleteAllByArtifactIds(c, deletedBy, artifactIds)
    }
  }

  def deleteAllByArtifactIds(c: Connection, deletedBy: UserReference, artifactIds: Seq[String]): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from project_libraries")
      .in("artifact_id", artifactIds)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteAllByGroupId(deletedBy: UserReference, groupId: String): Unit = {
    db.withConnection { c =>
      deleteAllByGroupId(c, deletedBy, groupId)
    }
  }

  def deleteAllByGroupId(c: Connection, deletedBy: UserReference, groupId: String): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from project_libraries")
      .equals("group_id", groupId)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteAllByGroupIds(deletedBy: UserReference, groupIds: Seq[String]): Unit = {
    db.withConnection { c =>
      deleteAllByGroupIds(c, deletedBy, groupIds)
    }
  }

  def deleteAllByGroupIds(c: Connection, deletedBy: UserReference, groupIds: Seq[String]): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from project_libraries")
      .in("group_id", groupIds)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteById(deletedBy: UserReference, id: String): Unit = {
    db.withConnection { c =>
      deleteById(c, deletedBy, id)
    }
  }

  def deleteById(c: Connection, deletedBy: UserReference, id: String): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from project_libraries")
      .equals("id", id)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteAllByIds(deletedBy: UserReference, ids: Seq[String]): Unit = {
    db.withConnection { c =>
      deleteAllByIds(c, deletedBy, ids)
    }
  }

  def deleteAllByIds(c: Connection, deletedBy: UserReference, ids: Seq[String]): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from project_libraries")
      .in("id", ids)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteAllByLibraryId(deletedBy: UserReference, libraryId: String): Unit = {
    db.withConnection { c =>
      deleteAllByLibraryId(c, deletedBy, libraryId)
    }
  }

  def deleteAllByLibraryId(c: Connection, deletedBy: UserReference, libraryId: String): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from project_libraries")
      .equals("library_id", libraryId)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteAllByLibraryIds(deletedBy: UserReference, libraryIds: Seq[String]): Unit = {
    db.withConnection { c =>
      deleteAllByLibraryIds(c, deletedBy, libraryIds)
    }
  }

  def deleteAllByLibraryIds(c: Connection, deletedBy: UserReference, libraryIds: Seq[String]): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from project_libraries")
      .in("library_id", libraryIds)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteAllByOrganizationId(deletedBy: UserReference, organizationId: String): Unit = {
    db.withConnection { c =>
      deleteAllByOrganizationId(c, deletedBy, organizationId)
    }
  }

  def deleteAllByOrganizationId(c: Connection, deletedBy: UserReference, organizationId: String): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from project_libraries")
      .equals("organization_id", organizationId)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteAllByOrganizationIds(deletedBy: UserReference, organizationIds: Seq[String]): Unit = {
    db.withConnection { c =>
      deleteAllByOrganizationIds(c, deletedBy, organizationIds)
    }
  }

  def deleteAllByOrganizationIds(c: Connection, deletedBy: UserReference, organizationIds: Seq[String]): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from project_libraries")
      .in("organization_id", organizationIds)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteAllByProjectId(deletedBy: UserReference, projectId: String): Unit = {
    db.withConnection { c =>
      deleteAllByProjectId(c, deletedBy, projectId)
    }
  }

  def deleteAllByProjectId(c: Connection, deletedBy: UserReference, projectId: String): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from project_libraries")
      .equals("project_id", projectId)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteAllByProjectIds(deletedBy: UserReference, projectIds: Seq[String]): Unit = {
    db.withConnection { c =>
      deleteAllByProjectIds(c, deletedBy, projectIds)
    }
  }

  def deleteAllByProjectIds(c: Connection, deletedBy: UserReference, projectIds: Seq[String]): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from project_libraries")
      .in("project_id", projectIds)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteAllByVersion(deletedBy: UserReference, version: String): Unit = {
    db.withConnection { c =>
      deleteAllByVersion(c, deletedBy, version)
    }
  }

  def deleteAllByVersion(c: Connection, deletedBy: UserReference, version: String): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from project_libraries")
      .equals("version", version)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteAllByVersions(deletedBy: UserReference, versions: Seq[String]): Unit = {
    db.withConnection { c =>
      deleteAllByVersions(c, deletedBy, versions)
    }
  }

  def deleteAllByVersions(c: Connection, deletedBy: UserReference, versions: Seq[String]): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from project_libraries")
      .in("version", versions)
      .anormSql.executeUpdate()(c)
      ()
  }

  private[this] val ValidCharacters: Set[String] = "_-,.abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".split("").toSet
  private[this] def isSafe(value: String): Boolean = value.trim.split("").forall(ValidCharacters.contains)
  def setJournalDeletedByUserId(c: Connection, deletedBy: UserReference): Unit = {
    assert(isSafe(deletedBy.id), s"Value '${deletedBy.id}' contains unsafe characters")
    anorm.SQL(s"SET journal.deleted_by_user_id = '${deletedBy.id}'").executeUpdate()(c)
  }

  def deleteAll(
    deletedBy: UserReference,
    ids: Option[Seq[String]],
    organizationId: Option[String],
    projectId: Option[String],
    projectIds: Option[Seq[String]],
    artifactId: Option[String],
    artifactIds: Option[Seq[String]],
    groupId: Option[String],
    groupIds: Option[Seq[String]],
    libraryId: Option[String],
    hasLibraryId: Option[Boolean],
    libraryIds: Option[Seq[String]],
    version: Option[String],
    versions: Option[Seq[String]]
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Int = {
    db.withConnection { implicit c =>
      deleteAllWithConnection(
        c,
        deletedBy = deletedBy,
        ids = ids,
        organizationId = organizationId,
        projectId = projectId,
        projectIds = projectIds,
        artifactId = artifactId,
        artifactIds = artifactIds,
        groupId = groupId,
        groupIds = groupIds,
        libraryId = libraryId,
        hasLibraryId = hasLibraryId,
        libraryIds = libraryIds,
        version = version,
        versions = versions
      )(customQueryModifier)
    }
  }

  def deleteAllWithConnection(
    c: java.sql.Connection,
    deletedBy: UserReference,
    ids: Option[Seq[String]],
    organizationId: Option[String],
    projectId: Option[String],
    projectIds: Option[Seq[String]],
    artifactId: Option[String],
    artifactIds: Option[Seq[String]],
    groupId: Option[String],
    groupIds: Option[Seq[String]],
    libraryId: Option[String],
    hasLibraryId: Option[Boolean],
    libraryIds: Option[Seq[String]],
    version: Option[String],
    versions: Option[Seq[String]]
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Int = {
    setJournalDeletedByUserId(c, deletedBy)

    val query = Query("delete from project_libraries")
    customQueryModifier(query)
      .optionalIn("project_libraries.id", ids)
      .equals("project_libraries.organization_id", organizationId)
      .equals("project_libraries.project_id", projectId)
      .optionalIn("project_libraries.project_id", projectIds)
      .equals("project_libraries.artifact_id", artifactId)
      .optionalIn("project_libraries.artifact_id", artifactIds)
      .equals("project_libraries.group_id", groupId)
      .optionalIn("project_libraries.group_id", groupIds)
      .equals("project_libraries.library_id", libraryId)
      .nullBoolean("project_libraries.library_id", hasLibraryId)
      .optionalIn("project_libraries.library_id", libraryIds)
      .equals("project_libraries.version", version)
      .optionalIn("project_libraries.version", versions)
      .anormSql()
      .executeUpdate()(c)
  }

}

object ProjectLibrariesDao {

  val parser: RowParser[ProjectLibrary] = {
    SqlParser.str("id") ~
    SqlParser.str("organization_id") ~
    SqlParser.str("project_id") ~
    SqlParser.str("group_id") ~
    SqlParser.str("artifact_id") ~
    SqlParser.str("version") ~
    SqlParser.str("cross_build_version").? ~
    SqlParser.str("path") ~
    SqlParser.str("library_id").? map {
      case id ~ organizationId ~ projectId ~ groupId ~ artifactId ~ version ~ crossBuildVersion ~ path ~ libraryId => ProjectLibrary(
        id = id,
        organizationId = organizationId,
        projectId = projectId,
        groupId = groupId,
        artifactId = artifactId,
        version = version,
        crossBuildVersion = crossBuildVersion,
        path = path,
        libraryId = libraryId
      )
    }
  }

}