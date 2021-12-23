package db

import javax.inject.{Inject, Singleton}
import io.flow.dependency.v0.models.{Library, LibraryVersion, SyncType, TaskDataSyncOne, VersionForm}
import io.flow.log.RollbarLogger
import io.flow.postgresql.{OrderBy, Query}
import io.flow.common.v0.models.UserReference
import io.flow.util.Version
import io.flow.util.IdGenerator
import anorm._
import play.api.db._

import scala.util.{Failure, Success, Try}

@Singleton
class LibraryVersionsDao @Inject()(
  db: Database,
  logger: RollbarLogger,
  internalTasksDao: InternalTasksDao
){

  private[this] val dbHelpers = DbHelpers(db, "library_versions")

  private[this] val BaseQuery = Query(s"""
    select library_versions.id,
           library_versions.version,
           library_versions.cross_build_version,
           libraries.id as library_id,
           libraries.group_id as library_group_id,
           libraries.artifact_id as library_artifact_id,
           organizations.id as library_organization_id,
           organizations.key as library_organization_key,
           resolvers.id as library_resolver_id,
           resolvers.visibility as library_resolver_visibility,
           resolvers.uri as library_resolver_uri,
           resolver_orgs.id as library_resolver_organization_id,
           resolver_orgs.key as library_resolver_organization_key
      from library_versions
      join libraries on libraries.id = library_versions.library_id
      join organizations on organizations.id = libraries.organization_id
      join resolvers on resolvers.id = libraries.resolver_id
      left join organizations resolver_orgs on resolver_orgs.id = resolvers.organization_id
  """)

  private[this] val InsertQuery = s"""
    insert into library_versions
    (id, library_id, version, cross_build_version, sort_key, updated_by_user_id)
    values
    ({id}, {library_id}, {version}, {cross_build_version}, {sort_key}, {updated_by_user_id})
  """

  def upsert(createdBy: UserReference, libraryId: String, form: VersionForm): LibraryVersion = {
    db.withConnection { implicit c =>
      upsertWithConnection(createdBy, libraryId, form)
    }
  }

  private[db] def upsertWithConnection(createdBy: UserReference, libraryId: String, form: VersionForm)(
    implicit c: java.sql.Connection
  ): LibraryVersion = {
    findAllWithConnection(
      Authorization.All,
      libraryId = Some(libraryId),
      version = Some(form.version),
      crossBuildVersion = Some(form.crossBuildVersion),
      limit = Some(1)
    ).headOption.getOrElse {
      Try {
        createWithConnection(createdBy, libraryId, form)
      } match {
        case Success(version) => {
          version
        }
        case Failure(ex) => {
          // check concurrent insert
          findAllWithConnection(
            Authorization.All,
            libraryId = Some(libraryId),
            version = Some(form.version),
            crossBuildVersion = Some(form.crossBuildVersion),
            limit = Some(1)
          ).headOption.getOrElse {
            logger.error(ex.getMessage, ex)
            sys.error(ex.getMessage)
          }
        }
      }
    }
  }

  def create(createdBy: UserReference, libraryId: String, form: VersionForm): LibraryVersion = {
    db.withConnection { implicit c =>
      createWithConnection(createdBy, libraryId, form)
    }
  }

  def createWithConnection(createdBy: UserReference, libraryId: String, form: VersionForm)(implicit c: java.sql.Connection): LibraryVersion = {
    val id = IdGenerator("liv").randomId()

    val sortKey = form.crossBuildVersion match {
      case None => Version(form.version).sortKey
      case Some(crossBuildVersion) => Version(s"${form.version}-$crossBuildVersion").sortKey
    }

    SQL(InsertQuery).on(
      Symbol("id") -> id,
      Symbol("library_id") -> libraryId,
      Symbol("version") -> form.version.trim,
      Symbol("cross_build_version") -> form.crossBuildVersion.map(_.trim),
      Symbol("sort_key") -> sortKey,
      Symbol("updated_by_user_id") -> createdBy.id
    ).execute()

    sync(libraryId)

    findByIdWithConnection(Authorization.All, id).getOrElse {
      sys.error("Failed to create version")
    }
  }

  def delete(deletedBy: UserReference, lv: LibraryVersion): Unit = {
    dbHelpers.delete(deletedBy.id, lv.id)
    sync(lv.library.id)
  }

  private[this] def sync(libraryId: String): Unit = {
    internalTasksDao.createSyncIfNotQueued(
      TaskDataSyncOne(libraryId, SyncType.Library)
    )
  }

  def findByLibraryAndVersionAndCrossBuildVersion(
    auth: Authorization,
    library: Library,
    version: String,
    crossBuildVersion: Option[String]
  ): Option[LibraryVersion] = {
    findAll(
      auth,
      libraryId = Some(library.id),
      version = Some(version),
      crossBuildVersion = Some(crossBuildVersion),
      limit = Some(1)
    ).headOption
  }

  def findById(
    auth: Authorization,
    id: String
  ): Option[LibraryVersion] = {
    db.withConnection { implicit c =>
      findByIdWithConnection(auth, id)
    }
  }

  def findByIdWithConnection(
    auth: Authorization,
    id: String
  ) (
    implicit c: java.sql.Connection
  ): Option[LibraryVersion] = {
    findAllWithConnection(auth, id = Some(id), limit = Some(1)).headOption
  }

  def findAll(
    auth: Authorization,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    libraryId: Option[String] = None,
    version: Option[String] = None,
    crossBuildVersion: Option[Option[String]] = None,
    greaterThanVersion: Option[String] = None,
    greaterThanSortKey: Option[String] = None,
    limit: Option[Long],
    offset: Long = 0
  ): Seq[LibraryVersion] = {
    db.withConnection { implicit c =>
      findAllWithConnection(
        auth,
        id = id,
        ids = ids,
        libraryId = libraryId,
        version = version,
        crossBuildVersion = crossBuildVersion,
        greaterThanVersion = greaterThanVersion,
        greaterThanSortKey = greaterThanSortKey,
        limit = limit,
        offset = offset
      )
    }
  }

  def findAllWithConnection(
    auth: Authorization,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    libraryId: Option[String] = None,
    version: Option[String] = None,
    crossBuildVersion: Option[Option[String]] = None,
    greaterThanVersion: Option[String] = None,
    greaterThanSortKey: Option[String] = None,
    orderBy: OrderBy = OrderBy("-library_versions.sort_key, library_versions.created_at"),
    limit: Option[Long],
    offset: Long = 0
  ) (
    implicit c: java.sql.Connection
  ): Seq[LibraryVersion] = {
    Standards.queryWithOptionalLimit(
      BaseQuery,
      tableName = "library_versions",
      auth = auth.organizations("organizations.id", Some("resolvers.visibility")),
      id = id,
      ids = ids,
      orderBy = orderBy.sql,
      limit = limit,
      offset = offset
    ).
      equals("library_versions.library_id", libraryId).
      optionalText(
        "library_versions.version",
        version,
        columnFunctions = Seq(Query.Function.Lower),
        valueFunctions = Seq(Query.Function.Lower, Query.Function.Trim)
      ).
      and(
        crossBuildVersion.map {
          case None => s"library_versions.cross_build_version is null"
          case Some(_) => s"lower(library_versions.cross_build_version) = lower(trim({cross_build_version}))"
        }
      ).bind("cross_build_version", crossBuildVersion.flatten).
      and(
        greaterThanVersion.map { _ =>
          """
            library_versions.sort_key > (
              select lv2.sort_key
                from library_versions lv2
               where lv2.library_id = library_versions.library_id
                 and lower(lv2.version) = lower(trim({greater_than_version}))
                 and ( (lv2.cross_build_version is null and library_versions.cross_build_version is null)
                       or
                       (lv2.cross_build_version = library_versions.cross_build_version) )
            )
          """
        }
      ).
      bind("greater_than_version", greaterThanVersion).
      greaterThan("library_versions.sort_key", greaterThanSortKey).
      as(
        io.flow.dependency.v0.anorm.parsers.LibraryVersion.parser().*
      )
  }

}
