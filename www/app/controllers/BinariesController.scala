package controllers

import io.flow.dependency.v0.errors.UnitResponse
import io.flow.dependency.v0.models.{Binary, SyncEvent}
import io.flow.dependency.www.lib.{Config, DependencyClientProvider}
import io.flow.play.util.{Pagination, PaginatedCollection}
import scala.concurrent.Future

import play.api._
import play.api.i18n.MessagesApi
import play.api.mvc._

class BinariesController @javax.inject.Inject() (
  val messagesApi: MessagesApi,
  override val tokenClient: io.flow.token.v0.interfaces.Client,
  override val dependencyClientProvider: DependencyClientProvider
) extends BaseController(tokenClient, dependencyClientProvider) {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def section = Some(io.flow.dependency.www.lib.Section.Binaries)

  def index(page: Int = 0) = Identified.async { implicit request =>
    for {
      binaries <- dependencyClient(request).binaries.get(
        limit = Pagination.DefaultLimit+1,
        offset = page * Pagination.DefaultLimit
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
  ) = Identified.async { implicit request =>
    withBinary(request, id) { binary =>
      for {
        versions <- dependencyClient(request).binaryVersions.get(
          binaryId = Some(id),
          limit = Config.VersionsPerPage+1,
          offset = versionsPage * Config.VersionsPerPage
        )
        projectBinaries <- dependencyClient(request).projectBinaries.get(
          binaryId = Some(id),
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
  ) = {
    dependencyClient(request).binaries.getById(id).flatMap { binary =>
      f(binary)
    }.recover {
      case UnitResponse(404) => {
        Redirect(routes.BinariesController.index()).flashing("warning" -> s"Binary not found")
      }
    }
  }

}
