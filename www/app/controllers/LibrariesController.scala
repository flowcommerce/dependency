package controllers

import io.flow.dependency.v0.errors.UnitResponse
import io.flow.dependency.v0.models.{Library, SyncEvent}
import io.flow.dependency.www.lib.{Config, DependencyClientProvider}
import io.flow.play.controllers.{FlowControllerComponents, IdentifiedRequest}
import io.flow.play.util.{PaginatedCollection, Pagination}
import io.flow.util.Config
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class LibrariesController @javax.inject.Inject() (
  val dependencyClientProvider: DependencyClientProvider,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents
)(implicit ec: ExecutionContext) extends BaseController(config, dependencyClientProvider) {

  override def section = Some(io.flow.dependency.www.lib.Section.Libraries)

  def index(page: Int = 0) = User.async { implicit request =>
    for {
      libraries <- dependencyClient(request).libraries.get(
        limit = Pagination.DefaultLimit.toLong + 1L,
        offset = page * Pagination.DefaultLimit.toLong
      )
    } yield {
      Ok(
        views.html.libraries.index(
          uiData(request),
          PaginatedCollection(page, libraries)
        )
      )
    }
  }

  def show(
    id: String,
    versionsPage: Int = 0,
    projectsPage: Int = 0
  ) = User.async { implicit request =>
    withLibrary(request, id) { library =>
      for {
        versions <- dependencyClient(request).libraryVersions.get(
          libraryId = Some(id),
          limit = Config.VersionsPerPage.toLong + 1L,
          offset = versionsPage * Config.VersionsPerPage.toLong
        )
        projectLibraries <- dependencyClient(request).projectLibraries.get(
          libraryId = Some(id),
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
          views.html.libraries.show(
            uiData(request),
            library,
            PaginatedCollection(versionsPage, versions, Config.VersionsPerPage),
            PaginatedCollection(projectsPage, projectLibraries),
            syncs.headOption
          )
        )
      }
    }
  }

  def withLibrary[T](
    request: IdentifiedRequest[T],
    id: String
  )(
    f: Library => Future[Result]
  ) = {
    dependencyClient(request).libraries.getById(id).flatMap { library =>
      f(library)
    }.recover {
      case UnitResponse(404) => {
        Redirect(routes.LibrariesController.index()).flashing("warning" -> s"Library not found")
      }
    }
  }

}
