package controllers

import io.flow.dependency.www.lib.DependencyClientProvider
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.{PaginatedCollection, Pagination}
import io.flow.util.Config
import play.api.mvc._

import scala.concurrent.ExecutionContext

class SearchController @javax.inject.Inject() (
  val dependencyClientProvider: DependencyClientProvider,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents
)(implicit ec: ExecutionContext) extends controllers.BaseController(config, dependencyClientProvider) {

  override def section = None

  def index(
    q: Option[String],
    page: Int
  ) = User.async { implicit request =>
    for {
      items <- dependencyClient(request).items.get(
        q = q,
        limit = Pagination.DefaultLimit.toLong + 1L,
        offset = page * Pagination.DefaultLimit.toLong
      )
    } yield {
      Ok(
        views.html.search.index(
          uiData(request).copy(
            title = q.map { v => s"Search results for $v" },
            query = q
          ),
          q,
          PaginatedCollection(page, items)
        )
      )
    }
  }

}
