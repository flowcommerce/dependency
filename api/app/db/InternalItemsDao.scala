package db

import cache.OrganizationsCache
import javax.inject.{Inject, Singleton}
import io.flow.dependency.v0.models.{Binary, BinarySummary, Item, ItemSummary, ItemSummaryUndefinedType, Library, LibrarySummary}
import io.flow.dependency.v0.models.{OrganizationSummary, Project, ProjectSummary, Visibility}
import io.flow.dependency.v0.models.json._
import io.flow.common.v0.models.UserReference
import io.flow.postgresql.OrderBy
import com.google.inject.Provider
import play.api.libs.json._

case class InternalItemForm(
  summary: ItemSummary,
  label: String,
  description: Option[String],
  contents: String,
  visibility: Visibility
) {

  val objectId: String = {
    summary match {
      case s: BinarySummary => s.id
      case s: LibrarySummary => s.id
      case s: ProjectSummary => s.id
      case ItemSummaryUndefinedType(name) => sys.error(s"Cannot get a id from ItemSummaryUndefinedType($name)")
    }
  }

  val organization: OrganizationSummary = {
    summary match {
      case s: BinarySummary => s.organization
      case s: LibrarySummary => s.organization
      case s: ProjectSummary => s.organization
      case ItemSummaryUndefinedType(name) => sys.error(s"Cannot get a id from ItemSummaryUndefinedType($name)")
    }
  }

  def dbForm: generated.ItemForm = {
    generated.ItemForm(
      organizationId = organization.id,
      objectId = objectId,
      visibility = visibility.toString,
      label = label,
      description = description,
      summary = Some(Json.toJson(summary)),
      contents = contents
    )
  }
}

@Singleton
class InternalItemsDao @Inject()(
  dao: generated.ItemsDao,
  librariesDaoProvider: Provider[LibrariesDao],
  projectsDaoProvider: Provider[ProjectsDao],
  organizationsCache: OrganizationsCache
){

  private[this] def visibility(summary: ItemSummary): Visibility = {
    summary match {
      case _: BinarySummary => {
        Visibility.Public
      }
      case s: LibrarySummary => {
        librariesDaoProvider.get.findById(Authorization.All, s.id).map(_.resolver.visibility).getOrElse(Visibility.Private)
      }
      case s: ProjectSummary => {
        projectsDaoProvider.get.findById(Authorization.All, s.id).map(_.visibility).getOrElse(Visibility.Private)
      }
      case ItemSummaryUndefinedType(_) => {
        Visibility.Private
      }
    }
  }

  def replaceBinary(user: UserReference, binary: Binary): Item = {
    val label = binary.name.toString
    val summary = BinarySummary(
      id = binary.id,
      organization = binary.organization,
      name = binary.name
    )
    replace(
      user,
      InternalItemForm(
        summary = summary,
        visibility = visibility(summary),
        label = label,
        description = None,
        contents = Seq(binary.id.toString, label).mkString(" ")
      )
    )
  }

  def replaceLibrary(user: UserReference, library: Library): Item = {
    val label = Seq(library.groupId, library.artifactId).mkString(".")
    val summary = LibrarySummary(
      id = library.id,
      organization = library.organization,
      groupId = library.groupId,
      artifactId = library.artifactId
    )
    replace(
      user,
      InternalItemForm(
        summary = summary,
        visibility = visibility(summary),
        label = label,
        description = None,
        contents = Seq(library.id.toString, label).mkString(" ")
      )
    )
  }

  def replaceProject(user: UserReference, project: Project): Item = {
    val label = project.name
    val description = project.uri
    val summary =  ProjectSummary(
      id = project.id,
      organization = project.organization,
      name = project.name
    )
    replace(
      user,
      InternalItemForm(
        summary = summary,
        visibility = visibility(summary),
        label = label,
        description = Some(description),
        contents = Seq(project.id.toString, label, description).mkString(" ")
      )
    )
  }

  def replace(user: UserReference, form: InternalItemForm): Item = {
    dao.upsertByObjectId(
      user,
      form.dbForm
    )
    findByObjectId(Authorization.All, form.objectId).getOrElse {
      sys.error("Failed to replace item")
    }
  }

  def delete(deletedBy: UserReference, item: Item): Unit = {
    dao.deleteById(deletedBy, item.id)
  }

  def deleteByObjectId(deletedBy: UserReference, objectId: String): Unit = {
    dao.deleteByObjectId(deletedBy, objectId)
  }

  def findById(auth: Authorization, id: String): Option[Item] = {
    findAll(auth, id = Some(id), limit = Some(1)).headOption
  }

  def findByObjectId(auth: Authorization, objectId: String): Option[Item] = {
    findAll(
      auth,
      objectId = Some(objectId),
      limit = Some(1)
    ).headOption
  }

  def findAll(
    auth: Authorization,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    organizationId: Option[String] = None,
    q: Option[String] = None,
    objectId: Option[String] = None,
    orderBy: OrderBy = OrderBy("-lower(items.label), items.created_at"),
    limit: Option[Long],
    offset: Long = 0
  ): Seq[Item] = {
    dao.findAll(
      ids = ids,
      organizationId = organizationId,
      objectId = objectId,
      limit = limit,
      offset = offset,
      orderBy = orderBy
    ) { queryMod =>
      queryMod.equals("id", id)
        .and(auth.organizations("items.organization_id", Some("items.visibility")).sql)
        .and(q.map { _ =>
          "items.contents like '%' || lower(trim({q})) || '%' "
        })
        .bind("q", q)
    }.map { toItem }
  }

  private[this] def toItem(db: generated.Item) = {
    Item(
      id = db.id,
      organization = OrganizationSummary(
        id = db.organizationId,
        key = organizationsCache.findByOrganizationId(db.organizationId).map(_.key).getOrElse {
          // TODO: Log error
          db.organizationId
        }
      ),
      visibility = Visibility(db.visibility),
      summary = db.summary.map(_.as[ItemSummary]).getOrElse {
        sys.error(s"Item[${db.id}] missing summary")
      },
      label = db.label,
      description = db.description
    )
  }
}
