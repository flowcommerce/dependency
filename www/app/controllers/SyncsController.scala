package controllers

import io.flow.dependency.www.lib.DependencyClientProvider
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.{PaginatedCollection, Pagination}
import io.flow.util.Config
import play.api.mvc._

import scala.concurrent.ExecutionContext

class SyncsController @javax.inject.Inject() (
  val dependencyClientProvider: DependencyClientProvider,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
)(implicit ec: ExecutionContext)
  extends controllers.BaseController(config, dependencyClientProvider) {

  override def section = None

  def index(objectId: Option[String], page: Int = 0) = User.async { implicit request =>
    for {
      syncs <- dependencyClient(request).syncs.get(
        objectId = objectId,
        limit = Pagination.DefaultLimit.toLong + 1L,
        offset = page * Pagination.DefaultLimit.toLong,
      )
    } yield {
      Ok(views.html.syncs.index(uiData(request), PaginatedCollection(page, syncs), objectId))
    }
  }

  def postBinariesById(id: String) = User.async { implicit request =>
    dependencyClient(request).syncs.postBinariesById(id).map { _ =>
      Redirect(routes.BinariesController.show(id)).flashing("success" -> "Sync requested")
    }
  }

  def postLibrariesById(id: String) = User.async { implicit request =>
    dependencyClient(request).syncs.postLibrariesById(id).map { _ =>
      Redirect(routes.LibrariesController.show(id)).flashing("success" -> "Sync requested")
    }
  }

  def postProjectsById(id: String) = User.async { implicit request =>
    dependencyClient(request).syncs.postProjectsById(id).map { _ =>
      Redirect(routes.ProjectsController.show(id)).flashing("success" -> "Sync requested")
    }
  }

}
