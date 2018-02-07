package db

import javax.inject.Inject

import io.flow.dependency.actors.MainActor
import io.flow.dependency.api.lib.Version
import io.flow.dependency.v0.models.{Binary, BinaryType, BinaryVersion}
import io.flow.postgresql.{OrderBy, Query}
import io.flow.common.v0.models.UserReference
import anorm._
import com.google.inject.Provider
import play.api.db._
import play.api.Play.current
import play.api.libs.json._

import scala.util.{Failure, Success, Try}

class BinaryVersionsDao @Inject()(
  db: Database,
  dbHelpersProvider: Provider[DbHelpers],
  @javax.inject.Named("main-actor") mainActor: akka.actor.ActorRef
){

  private[this] val BaseQuery = Query(s"""
    select binary_versions.id,
           binary_versions.version,
           binaries.id as binary_id,
           binaries.name as binary_name,
           organizations.id as binary_organization_id,
           organizations.key as binary_organization_key
      from binary_versions
      join binaries on binaries.id = binary_versions.binary_id
      left join organizations on organizations.id = binaries.organization_id
  """)

  private[this] val InsertQuery = s"""
    insert into binary_versions
    (id, binary_id, version, sort_key, updated_by_user_id)
    values
    ({id}, {binary_id}, {version}, {sort_key}, {updated_by_user_id})
  """

  def upsert(createdBy: UserReference, binaryId: String, version: String): BinaryVersion = {
    db.withConnection { implicit c =>
      upsertWithConnection(createdBy, binaryId, version)
    }
  }

  private[db] def upsertWithConnection(createdBy: UserReference, binaryId: String, version: String)(
    implicit c: java.sql.Connection
  ): BinaryVersion = {
    findAllWithConnection(
      Authorization.All,
      binaryId = Some(binaryId),
      version = Some(version),
      limit = 1
    ).headOption.getOrElse {
      Try {
        createWithConnection(createdBy, binaryId, version)
      } match {
        case Success(version) => version
        case Failure(ex) => {
          findAllWithConnection(
            Authorization.All,
            binaryId = Some(binaryId),
            version = Some(version),
            limit = 1
          ).headOption.getOrElse {
            play.api.Logger.error(ex.getMessage, ex)
            sys.error(ex.getMessage)
          }
        }
      }
    }
  }

  def create(createdBy: UserReference, binaryId: String, version: String): BinaryVersion = {
    db.withConnection { implicit c =>
      createWithConnection(createdBy, binaryId, version)
    }
  }

  def createWithConnection(createdBy: UserReference, binaryId: String, version: String)(implicit c: java.sql.Connection): BinaryVersion = {
    assert(!version.trim.isEmpty, "Version must be non empty")
    val id = io.flow.play.util.IdGenerator("biv").randomId()

    SQL(InsertQuery).on(
      'id -> id,
      'binary_id -> binaryId,
      'version -> version.trim,
      'sort_key -> Version(version.trim).sortKey,
      'updated_by_user_id -> createdBy.id
    ).execute()

    mainActor ! MainActor.Messages.BinaryVersionCreated(id, binaryId)

    findByIdWithConnection(Authorization.All, id).getOrElse {
      sys.error("Failed to create version")
    }
  }

  def delete(deletedBy: UserReference, bv: BinaryVersion) {
    dbHelpersProvider.get.delete("binary_versions", deletedBy.id, bv.id)
    mainActor ! MainActor.Messages.BinaryVersionDeleted(bv.id, bv.binary.id)
  }

  def findByBinaryAndVersion(
    auth: Authorization,
    binary: Binary, version: String
  ): Option[BinaryVersion] = {
    findAll(
      auth,
      binaryId = Some(binary.id),
      version = Some(version),
      limit = 1
    ).headOption
  }

  def findById(
    auth: Authorization,
    id: String
  ): Option[BinaryVersion] = {
    db.withConnection { implicit c =>
      findByIdWithConnection(auth, id)
    }
  }

  def findByIdWithConnection(
    auth: Authorization,
    id: String
  ) (
    implicit c: java.sql.Connection
  ): Option[BinaryVersion] = {
    findAllWithConnection(auth, id = Some(id), limit = 1).headOption
  }

  def findAll(
    auth: Authorization,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    binaryId: Option[String] = None,
    projectId: Option[String] = None,
    version: Option[String] = None,
    greaterThanVersion: Option[String] = None,
    limit: Long = 25,
    offset: Long = 0
  ) = {
    db.withConnection { implicit c =>
      findAllWithConnection(
        auth,
        id = id,
        ids = ids,
        binaryId = binaryId,
        projectId = projectId,
        version = version,
        greaterThanVersion = greaterThanVersion,
        limit = limit,
        offset = offset
      )
    }
  }

  def findAllWithConnection(
    auth: Authorization,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    binaryId: Option[String] = None,
    projectId: Option[String] = None,
    version: Option[String] = None,
    greaterThanVersion: Option[String] = None,
    orderBy: OrderBy = OrderBy(s"-binary_versions.sort_key, binary_versions.created_at"),
    limit: Long = 25,
    offset: Long = 0
  ) (
    implicit c: java.sql.Connection
  ): Seq[BinaryVersion] = {
    // N.B.: at this time, all binary versions are public and thus we
    // do not need to filter by auth. It is here in the API for
    // consistency and to explicitly declare we are respecting it.

    BaseQuery.
      equals("binary_versions.id", id).
      optionalIn("binary_versions.id", ids).
      equals("binary_versions.binary_id", binaryId).
      and(
        projectId.map { id =>
          "binary_versions.binary_id in (select binary_id from project_bainaries where binary_id is not null and project_id = {project_id})"
        }
      ).bind("project_id", projectId).
      optionalText(
        "binary_versions.version",
        version,
        columnFunctions = Seq(Query.Function.Lower),
        valueFunctions = Seq(Query.Function.Lower, Query.Function.Trim)
      ).
      and(
        greaterThanVersion.map { v =>
          s"binary_versions.sort_key > {greater_than_version_sort_key}"
        }
      ).bind("greater_than_version_sort_key", greaterThanVersion).
      orderBy(orderBy.sql).
      limit(limit).
      offset(offset).
      as(
        io.flow.dependency.v0.anorm.parsers.BinaryVersion.parser().*
      )
  }

}

