package controllers

import io.flow.dependency.v0.errors.UnitResponse
import io.flow.dependency.v0.models.{Binary, SyncEvent}
import io.flow.dependency.www.lib.{Config, DependencyClientProvider}
import io.flow.play.controllers.{FlowControllerComponents, IdentifiedRequest}
import io.flow.play.util.{Config, PaginatedCollection, Pagination}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class BinariesController @javax.inject.Inject()(
  dependencyClientProvider: DependencyClientProvider,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents
)(implicit ec: ExecutionContext) extends BaseController(config, dependencyClientProvider) {

  override def section = Some(io.flow.dependency.www.lib.Section.Binaries)

  def index(page: Int = 0): Action[AnyContent] = User.async { implicit request =>
    for {
      binaries <- dependencyClient(request).binaries.get(
        limit = Pagination.DefaultLimit.toLong + 1L,
        offset = page * Pagination.DefaultLimit.toLong
      )
    } yield {
      Ok(
        views.html.binaries.index(
          uiData(request),
          PaginatedCollection(page, binaries)
        )
      )
    }
  }

  def show(
    id: String,
    versionsPage: Int = 0,
    projectsPage: Int = 0
  ): Action[AnyContent] = User.async { implicit request =>
    withBinary(request, id) { binary =>
      for {
        versions <- dependencyClient(request).binaryVersions.get(
          binaryId = Some(id),
          limit = Config.VersionsPerPage.toLong + 1L,
          offset = versionsPage * Config.VersionsPerPage.toLong
        )
        projectBinaries <- dependencyClient(request).projectBinaries.get(
          binaryId = Some(id),
          limit = Pagination.DefaultLimit.toLong + 1L,
          offset = projectsPage * Pagination.DefaultLimit.toLong
        )
        syncs <- dependencyClient(request).syncs.get(
          objectId = Some(id),
          event = Some(SyncEvent.Completed),
          limit = 1
        )
      } yield {
        Ok(
          views.html.binaries.show(
            uiData(request),
            binary,
            PaginatedCollection(versionsPage, versions, Config.VersionsPerPage),
            PaginatedCollection(projectsPage, projectBinaries),
            syncs.headOption
          )
        )
      }
    }
  }

  def withBinary[T](
    request: IdentifiedRequest[T],
    id: String
  )(
    f: Binary => Future[Result]
  ): Future[Result] = {
    dependencyClient(request).binaries.getById(id).flatMap { binary =>
      f(binary)
    }.recover {
      case UnitResponse(404) => {
        Redirect(routes.BinariesController.index()).flashing("warning" -> s"Binary not found")
      }
    }
  }

}
