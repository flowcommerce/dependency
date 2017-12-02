package db

import com.bryzek.dependency.actors.MainActor
import com.bryzek.dependency.v0.models.{Binary, BinaryForm, SyncEvent}
import io.flow.common.v0.models.UserReference
import io.flow.postgresql.{Query, OrderBy, Pager}
import anorm._
import play.api.db._
import play.api.Play.current

object BinariesDao {

  private[this] val BaseQuery = Query(s"""
    select binaries.id,
           binaries.name,
           organizations.id as organization_id,
           organizations.key as organization_key
      from binaries
      left join organizations on organizations.id = binaries.organization_id
  """)

  private[this] val InsertQuery = """
    insert into binaries
    (id, organization_id, name, updated_by_user_id)
    values
    ({id}, {organization_id}, {name}, {updated_by_user_id})
  """

  private[db] def validate(
    form: BinaryForm
  ): Seq[String] = {
    if (form.name.toString.trim == "") {
      Seq("Name cannot be empty")

    } else {
      BinariesDao.findByName(Authorization.All, form.name.toString) match {
        case None => Seq.empty
        case Some(_) => Seq("Binary with this name already exists")
      }
    }
  }

  def upsert(createdBy: UserReference, form: BinaryForm): Either[Seq[String], Binary] = {
    BinariesDao.findByName(Authorization.All, form.name.toString) match {
      case Some(binary) => Right(binary)
      case None => create(createdBy, form)
    }
  }

  def create(createdBy: UserReference, form: BinaryForm): Either[Seq[String], Binary] = {
    validate(form) match {
      case Nil => {
        val id = io.flow.play.util.IdGenerator("bin").randomId()

        DB.withConnection { implicit c =>
          SQL(InsertQuery).on(
            'id -> id,
            'organization_id -> form.organizationId,
            'name -> form.name.toString.toLowerCase,
            'updated_by_user_id -> createdBy.id
          ).execute()
        }

        MainActor.ref ! MainActor.Messages.BinaryCreated(id)

        Right(
          findById(Authorization.All, id).getOrElse {
            sys.error("Failed to create binary")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def delete(deletedBy: UserReference, binary: Binary) {
    Pager.create { offset =>
      BinaryVersionsDao.findAll(Authorization.All, binaryId = Some(binary.id), offset = offset)
    }.foreach { BinaryVersionsDao.delete(deletedBy, _) }

    DbHelpers.delete("binaries", deletedBy.id, binary.id)
    MainActor.ref ! MainActor.Messages.BinaryDeleted(binary.id)
  }

  def findByName(auth: Authorization, name: String): Option[Binary] = {
    findAll(auth, name = Some(name), limit = 1).headOption
  }

  def findById(auth: Authorization, id: String): Option[Binary] = {
    findAll(auth, id = Some(id), limit = 1).headOption
  }

  /**
    * @param auth: Included here for symmetry with other APIs but at the
    *  moment all binary data are public.
    */
  def findAll(
    auth: Authorization,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    projectId: Option[String] = None,
    organizationId: Option[String] = None,
    name: Option[String] = None,
    isSynced: Option[Boolean] = None,
    orderBy: OrderBy = OrderBy(s"-lower(binaries.name),binaries.created_at"),
    limit: Long = 25,
    offset: Long = 0
  ): Seq[Binary] = {
    DB.withConnection { implicit c =>
      BaseQuery.
        equals("binaries.id", id).
        optionalIn("binaries.id", ids).
        and (
          projectId.map { id =>
            s"binaries.id in (select binary_id from project_binaries where binary_id is not null and project_id = {project_id})"
          }
        ).bind("project_id", projectId).
        equals("binaries.organization_id", organizationId).
        optionalText(
          "binaries.name",
          name,
          columnFunctions = Seq(Query.Function.Lower),
          valueFunctions = Seq(Query.Function.Lower, Query.Function.Trim)
        ).
        and(
          isSynced.map { value =>
            val clause = "select 1 from syncs where object_id = binaries.id and event = {sync_event_completed}"
            if (value) {
              s"exists ($clause)"
            } else {
              s"not exists ($clause)"
            }
          }
        ).
        bind("sync_event_completed", SyncEvent.Completed.toString).
        orderBy(orderBy.sql).
        limit(limit).
        offset(offset).
        as(
          com.bryzek.dependency.v0.anorm.parsers.Binary.parser().*
        )
    }
  }

}
