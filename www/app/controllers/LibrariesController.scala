package controllers

import com.bryzek.dependency.v0.errors.UnitResponse
import com.bryzek.dependency.v0.models.{Library, LibraryForm, SyncEvent}
import com.bryzek.dependency.www.lib.{Config, DependencyClientProvider}
import io.flow.play.util.{Pagination, PaginatedCollection}
import scala.concurrent.Future

import play.api._
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

class LibrariesController @javax.inject.Inject() (
  val messagesApi: MessagesApi,
  override val tokenClient: io.flow.token.v0.interfaces.Client,
  override val dependencyClientProvider: DependencyClientProvider
) extends BaseController(tokenClient, dependencyClientProvider) {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def section = Some(com.bryzek.dependency.www.lib.Section.Libraries)

  def index(page: Int = 0) = Identified.async { implicit request =>
    for {
      libraries <- dependencyClient(request).libraries.get(
        limit = Pagination.DefaultLimit+1,
        offset = page * Pagination.DefaultLimit
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
  ) = Identified.async { implicit request =>
    withLibrary(request, id) { library =>
      for {
        versions <- dependencyClient(request).libraryVersions.get(
          libraryId = Some(id),
          limit = Config.VersionsPerPage+1,
          offset = versionsPage * Config.VersionsPerPage
        )
        projectLibraries <- dependencyClient(request).projectLibraries.get(
          libraryId = Some(id),
          limit = Pagination.DefaultLimit+1,
          offset = projectsPage * Pagination.DefaultLimit
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
