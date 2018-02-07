package controllers

import io.flow.dependency.www.lib.DependencyClientProvider
import io.flow.dependency.controllers.helpers.DependencyUiControllerHelper
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.{Config, PaginatedCollection, Pagination}
import play.api._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._

class ApplicationController @javax.inject.Inject()(
  val dependencyClientProvider: DependencyClientProvider,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents
) extends BaseController(config, dependencyClientProvider) {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def section = Some(io.flow.dependency.www.lib.Section.Dashboard)

  def redirect = Action { request =>
    Redirect(request.path + "/")
  }

  def index(organization: Option[String], page: Int = 0) = User.async { implicit request =>
    for {
      recommendations <- dependencyClient(request).recommendations.get(
        organization = organization,
        limit = Pagination.DefaultLimit + 1,
        offset = page * Pagination.DefaultLimit
      )
    } yield {
      Ok(
        views.html.index(
          uiData(request).copy(organization = organization),
          PaginatedCollection(page, recommendations)
        )
      )
    }
  }


}
