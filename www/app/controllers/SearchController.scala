package controllers

import io.flow.dependency.www.lib.DependencyClientProvider
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.{Config, PaginatedCollection, Pagination}
import play.api.mvc._

class SearchController @javax.inject.Inject() (
  val dependencyClientProvider: DependencyClientProvider,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents
) extends BaseController(config, dependencyClientProvider) {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def section = None

  def index(
    q: Option[String],
    page: Int
  ) = Identified.async { implicit request =>
    for {
      items <- dependencyClient(request).items.get(
        q = q,
        limit = Pagination.DefaultLimit+1,
        offset = page * Pagination.DefaultLimit
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
