package controllers

import io.flow.dependency.www.lib.{DependencyClientProvider, Section}
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.{PaginatedCollection, Pagination}
import io.flow.util.Config
import play.api.mvc.{Action, AnyContent, ControllerComponents}

import scala.concurrent.ExecutionContext

class ApplicationController @javax.inject.Inject()(
  val dependencyClientProvider: DependencyClientProvider,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents
)(implicit ec: ExecutionContext) extends BaseController(config, dependencyClientProvider) {

  override def section: Some[Section.Dashboard.type] = Some(Section.Dashboard)

  def redirect: Action[AnyContent] = Action { request =>
    Redirect(request.path + "/")
  }

  def index(organization: Option[String], page: Int = 0): Action[AnyContent] = User.async { implicit request =>
    for {
      recommendations <- dependencyClient(request).recommendations.get(
        organization = organization,
        limit = Pagination.DefaultLimit.toLong + 1L,
        offset = page * Pagination.DefaultLimit.toLong
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
