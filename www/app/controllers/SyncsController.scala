package controllers

import io.flow.dependency.v0.errors.UnitResponse
import io.flow.dependency.v0.models.Sync
import io.flow.dependency.www.lib.DependencyClientProvider
import io.flow.common.v0.models.User
import io.flow.play.util.{Pagination, PaginatedCollection}
import scala.concurrent.Future

import play.api._
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

class SyncsController @javax.inject.Inject() (
  val messagesApi: MessagesApi,
  override val tokenClient: io.flow.token.v0.interfaces.Client,
  override val dependencyClientProvider: DependencyClientProvider
) extends BaseController(tokenClient, dependencyClientProvider) {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def section = None

  def index(objectId: Option[String], page: Int = 0) = Identified.async { implicit request =>
    for {
      syncs <- dependencyClient(request).syncs.get(
        objectId = objectId,
        limit = Pagination.DefaultLimit+1,
        offset = page * Pagination.DefaultLimit
      )
    } yield {
      Ok(views.html.syncs.index(uiData(request), PaginatedCollection(page, syncs), objectId))
    }
  }

  def postBinariesById(id: String) = Identified.async { implicit request =>
    dependencyClient(request).syncs.postBinariesById(id).map { _ =>
      Redirect(routes.BinariesController.show(id)).flashing("success" -> "Sync requested")
    }
  }

  def postLibrariesById(id: String) = Identified.async { implicit request =>
    dependencyClient(request).syncs.postLibrariesById(id).map { _ =>
      Redirect(routes.LibrariesController.show(id)).flashing("success" -> "Sync requested")
    }
  }

  def postProjectsById(id: String) = Identified.async { implicit request =>
    dependencyClient(request).syncs.postProjectsById(id).map { _ =>
      Redirect(routes.ProjectsController.show(id)).flashing("success" -> "Sync requested")
    }
  }

}
