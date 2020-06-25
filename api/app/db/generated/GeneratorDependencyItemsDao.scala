package db.generated

import anorm.JodaParameterMetaData._
import anorm._
import io.flow.common.v0.models.UserReference
import io.flow.postgresql.{OrderBy, Query}
import io.flow.util.IdGenerator
import java.sql.Connection
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.db.Database
import play.api.libs.json.{JsObject, JsValue, Json}

case class Item(
  id: String,
  organizationId: String,
  visibility: String,
  objectId: String,
  label: String,
  description: Option[String],
  summary: Option[JsObject],
  contents: String,
  createdAt: DateTime,
  updatedAt: DateTime
) {

  lazy val form: ItemForm = ItemForm(
    organizationId = organizationId,
    visibility = visibility,
    objectId = objectId,
    label = label,
    description = description,
    summary = summary,
    contents = contents
  )

}

case class ItemForm(
  organizationId: String,
  visibility: String,
  objectId: String,
  label: String,
  description: Option[String],
  summary: Option[JsValue],
  contents: String
) {
  assert(
    summary.forall(_.isInstanceOf[JsObject]),
    s"Field[summary] must be a JsObject and not a ${summary.map(_.getClass.getName).getOrElse("Unknown")}"
  )
}

object ItemsTable {
  val Schema: String = "public"
  val Name: String = "items"

  object Columns {
    val Id: String = "id"
    val OrganizationId: String = "organization_id"
    val Visibility: String = "visibility"
    val ObjectId: String = "object_id"
    val Label: String = "label"
    val Description: String = "description"
    val Summary: String = "summary"
    val Contents: String = "contents"
    val CreatedAt: String = "created_at"
    val UpdatedAt: String = "updated_at"
    val UpdatedByUserId: String = "updated_by_user_id"
    val HashCode: String = "hash_code"
    val all: List[String] = List(Id, OrganizationId, Visibility, ObjectId, Label, Description, Summary, Contents, CreatedAt, UpdatedAt, UpdatedByUserId, HashCode)
  }
}

@Singleton
class ItemsDao @Inject() (
  val db: Database
) {

  private[this] val idGenerator = IdGenerator("itm")

  def randomId(): String = idGenerator.randomId()

  private[this] val BaseQuery = Query("""
      | select items.id,
      |        items.organization_id,
      |        items.visibility,
      |        items.object_id,
      |        items.label,
      |        items.description,
      |        items.summary::text as summary_text,
      |        items.contents,
      |        items.created_at,
      |        items.updated_at,
      |        items.updated_by_user_id,
      |        items.hash_code
      |   from items
  """.stripMargin)

  private[this] val UpsertQuery = Query("""
    | insert into items
    | (id, organization_id, visibility, object_id, label, description, summary, contents, updated_by_user_id, hash_code)
    | values
    | ({id}, {organization_id}, {visibility}, {object_id}, {label}, {description}, {summary}::json, {contents}, {updated_by_user_id}, {hash_code}::bigint)
    | on conflict (object_id)
    | do update
    |    set organization_id = {organization_id},
    |        visibility = {visibility},
    |        label = {label},
    |        description = {description},
    |        summary = {summary}::json,
    |        contents = {contents},
    |        updated_by_user_id = {updated_by_user_id},
    |        hash_code = {hash_code}::bigint
    |  where items.hash_code != {hash_code}::bigint
    | returning id
  """.stripMargin)

  private[this] val UpdateQuery = Query("""
    | update items
    |    set organization_id = {organization_id},
    |        visibility = {visibility},
    |        object_id = {object_id},
    |        label = {label},
    |        description = {description},
    |        summary = {summary}::json,
    |        contents = {contents},
    |        updated_by_user_id = {updated_by_user_id},
    |        hash_code = {hash_code}::bigint
    |  where id = {id}
    |    and items.hash_code != {hash_code}::bigint
  """.stripMargin)

  private[this] def bindQuery(query: Query, form: ItemForm): Query = {
    query.
      bind("organization_id", form.organizationId).
      bind("visibility", form.visibility).
      bind("object_id", form.objectId).
      bind("label", form.label).
      bind("description", form.description).
      bind("summary", form.summary).
      bind("contents", form.contents).
      bind("hash_code", form.hashCode())
  }

  private[this] def toNamedParameter(updatedBy: UserReference, form: ItemForm): Seq[NamedParameter] = {
    Seq(
      scala.Symbol("id") -> randomId(),
      scala.Symbol("organization_id") -> form.organizationId,
      scala.Symbol("visibility") -> form.visibility,
      scala.Symbol("object_id") -> form.objectId,
      scala.Symbol("label") -> form.label,
      scala.Symbol("description") -> form.description,
      scala.Symbol("summary") -> form.summary.map { _.toString },
      scala.Symbol("contents") -> form.contents,
      scala.Symbol("updated_by_user_id") -> updatedBy.id,
      scala.Symbol("hash_code") -> form.hashCode()
    )
  }

  def upsertIfChangedByObjectId(updatedBy: UserReference, form: ItemForm): Unit = {
    if (!findByObjectId(form.objectId).map(_.form).contains(form)) {
      upsertByObjectId(updatedBy, form)
    }
  }

  def upsertByObjectId(updatedBy: UserReference, form: ItemForm): Unit = {
    db.withConnection { c =>
      upsertByObjectId(c, updatedBy, form)
    }
  }

  def upsertByObjectId(c: Connection, updatedBy: UserReference, form: ItemForm): Unit = {
    bindQuery(UpsertQuery, form).
      bind("id", randomId()).
      bind("updated_by_user_id", updatedBy.id).
      anormSql.execute()(c)
    ()
  }

  def upsertBatchByObjectId(updatedBy: UserReference, forms: Seq[ItemForm]): Unit = {
    db.withConnection { c =>
      upsertBatchByObjectId(c, updatedBy, forms)
    }
  }

  def upsertBatchByObjectId(c: Connection, updatedBy: UserReference, forms: Seq[ItemForm]): Unit = {
    if (forms.nonEmpty) {
      val params = forms.map(toNamedParameter(updatedBy, _))
      BatchSql(UpsertQuery.sql(), params.head, params.tail: _*).execute()(c)
      ()
    }
  }

  def updateIfChangedById(updatedBy: UserReference, id: String, form: ItemForm): Unit = {
    if (!findById(id).map(_.form).contains(form)) {
      updateById(updatedBy, id, form)
    }
  }

  def updateById(updatedBy: UserReference, id: String, form: ItemForm): Unit = {
    db.withConnection { c =>
      updateById(c, updatedBy, id, form)
    }
  }

  def updateById(c: Connection, updatedBy: UserReference, id: String, form: ItemForm): Unit = {
    bindQuery(UpdateQuery, form).
      bind("id", id).
      bind("updated_by_user_id", updatedBy.id).
      anormSql.execute()(c)
    ()
  }

  def update(updatedBy: UserReference, existing: Item, form: ItemForm): Unit = {
    db.withConnection { c =>
      update(c, updatedBy, existing, form)
    }
  }

  def update(c: Connection, updatedBy: UserReference, existing: Item, form: ItemForm): Unit = {
    updateById(c, updatedBy, existing.id, form)
  }

  def findById(id: String): Option[Item] = {
    db.withConnection { c =>
      findByIdWithConnection(c, id)
    }
  }

  def findByIdWithConnection(c: java.sql.Connection, id: String): Option[Item] = {
    findAllWithConnection(c, ids = Some(Seq(id)), limit = Some(1L)).headOption
  }

  def findByObjectId(objectId: String): Option[Item] = {
    db.withConnection { c =>
      findByObjectIdWithConnection(c, objectId)
    }
  }

  def findByObjectIdWithConnection(c: java.sql.Connection, objectId: String): Option[Item] = {
    findAllWithConnection(c, objectId = Some(objectId), limit = Some(1L)).headOption
  }

  def iterateAll(
    ids: Option[Seq[String]] = None,
    objectId: Option[String] = None,
    objectIds: Option[Seq[String]] = None,
    organizationId: Option[String] = None,
    organizationIds: Option[Seq[String]] = None,
    pageSize: Long = 2000L,
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Iterator[Item] = {
    def iterate(lastValue: Option[Item]): Iterator[Item] = {
      val page = findAll(
        ids = ids,
        objectId = objectId,
        objectIds = objectIds,
        organizationId = organizationId,
        organizationIds = organizationIds,
        limit = Some(pageSize),
        orderBy = OrderBy("items.id"),
      ) { q => customQueryModifier(q).greaterThan("items.id", lastValue.map(_.id)) }

      page.lastOption match {
        case None => Iterator.empty
        case lastValue => page.iterator ++ iterate(lastValue)
      }
    }

    iterate(None)
  }

  def findAll(
    ids: Option[Seq[String]] = None,
    objectId: Option[String] = None,
    objectIds: Option[Seq[String]] = None,
    organizationId: Option[String] = None,
    organizationIds: Option[Seq[String]] = None,
    limit: Option[Long],
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("items.id")
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Seq[Item] = {
    db.withConnection { c =>
      findAllWithConnection(
        c,
        ids = ids,
        objectId = objectId,
        objectIds = objectIds,
        organizationId = organizationId,
        organizationIds = organizationIds,
        limit = limit,
        offset = offset,
        orderBy = orderBy
      )(customQueryModifier)
    }
  }

  def findAllWithConnection(
    c: java.sql.Connection,
    ids: Option[Seq[String]] = None,
    objectId: Option[String] = None,
    objectIds: Option[Seq[String]] = None,
    organizationId: Option[String] = None,
    organizationIds: Option[Seq[String]] = None,
    limit: Option[Long],
    offset: Long = 0,
    orderBy: OrderBy = OrderBy("items.id")
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Seq[Item] = {
    customQueryModifier(BaseQuery).
      optionalIn("items.id", ids).
      equals("items.object_id", objectId).
      optionalIn("items.object_id", objectIds).
      equals("items.organization_id", organizationId).
      optionalIn("items.organization_id", organizationIds).
      optionalLimit(limit).
      offset(offset).
      orderBy(orderBy.sql).
      as(ItemsDao.parser.*)(c)
  }

  def delete(deletedBy: UserReference, item: Item): Unit = {
    db.withConnection { c =>
      delete(c, deletedBy, item)
    }
  }

  def delete(c: Connection, deletedBy: UserReference, item: Item): Unit = {
    deleteById(c, deletedBy, item.id)
  }

  def deleteById(deletedBy: UserReference, id: String): Unit = {
    db.withConnection { c =>
      deleteById(c, deletedBy, id)
    }
  }

  def deleteById(c: Connection, deletedBy: UserReference, id: String): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from items")
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
    Query("delete from items")
      .in("id", ids)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteByObjectId(deletedBy: UserReference, objectId: String): Unit = {
    db.withConnection { c =>
      deleteByObjectId(c, deletedBy, objectId)
    }
  }

  def deleteByObjectId(c: Connection, deletedBy: UserReference, objectId: String): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from items")
      .equals("object_id", objectId)
      .anormSql.executeUpdate()(c)
      ()
  }

  def deleteAllByObjectIds(deletedBy: UserReference, objectIds: Seq[String]): Unit = {
    db.withConnection { c =>
      deleteAllByObjectIds(c, deletedBy, objectIds)
    }
  }

  def deleteAllByObjectIds(c: Connection, deletedBy: UserReference, objectIds: Seq[String]): Unit = {
    setJournalDeletedByUserId(c, deletedBy)
    Query("delete from items")
      .in("object_id", objectIds)
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
    Query("delete from items")
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
    Query("delete from items")
      .in("organization_id", organizationIds)
      .anormSql.executeUpdate()(c)
      ()
  }

  private[this] val ValidCharacters: Set[String] = "_-,.abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".split("").toSet
  private[this] def isSafe(value: String): Boolean = value.trim.split("").forall(ValidCharacters.contains)
  def setJournalDeletedByUserId(c: Connection, deletedBy: UserReference): Unit = {
    assert(isSafe(deletedBy.id), s"Value '${deletedBy.id}' contains unsafe characters")
    anorm.SQL(s"SET journal.deleted_by_user_id = '${deletedBy.id}'").executeUpdate()(c)
    ()
  }

  def deleteAll(
    deletedBy: UserReference,
    ids: Option[Seq[String]],
    objectId: Option[String],
    objectIds: Option[Seq[String]],
    organizationId: Option[String]
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Int = {
    db.withConnection { implicit c =>
      deleteAllWithConnection(
        c,
        deletedBy = deletedBy,
        ids = ids,
        objectId = objectId,
        objectIds = objectIds,
        organizationId = organizationId
      )(customQueryModifier)
    }
  }

  def deleteAllWithConnection(
    c: java.sql.Connection,
    deletedBy: UserReference,
    ids: Option[Seq[String]],
    objectId: Option[String],
    objectIds: Option[Seq[String]],
    organizationId: Option[String]
  ) (
    implicit customQueryModifier: Query => Query = { q => q }
  ): Int = {
    setJournalDeletedByUserId(c, deletedBy)

    val query = Query("delete from items")
    customQueryModifier(query)
      .optionalIn("items.id", ids)
      .equals("items.object_id", objectId)
      .optionalIn("items.object_id", objectIds)
      .equals("items.organization_id", organizationId)
      .anormSql()
      .executeUpdate()(c)
  }

}

object ItemsDao {

  val parser: RowParser[Item] = {
    SqlParser.str("id") ~
    SqlParser.str("organization_id") ~
    SqlParser.str("visibility") ~
    SqlParser.str("object_id") ~
    SqlParser.str("label") ~
    SqlParser.str("description").? ~
    SqlParser.str("summary_text").? ~
    SqlParser.str("contents") ~
    SqlParser.get[DateTime]("created_at") ~
    SqlParser.get[DateTime]("updated_at") map {
      case id ~ organizationId ~ visibility ~ objectId ~ label ~ description ~ summary ~ contents ~ createdAt ~ updatedAt => Item(
        id = id,
        organizationId = organizationId,
        visibility = visibility,
        objectId = objectId,
        label = label,
        description = description,
        summary = summary.map { text => Json.parse(text).as[JsObject] },
        contents = contents,
        createdAt = createdAt,
        updatedAt = updatedAt
      )
    }
  }

}