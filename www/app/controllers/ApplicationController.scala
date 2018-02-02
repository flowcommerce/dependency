package controllers

import io.flow.dependency.www.lib.DependencyClientProvider
import io.flow.play.util.{Pagination, PaginatedCollection}
import play.api._
import play.api.i18n.MessagesApi
import play.api.mvc._

class ApplicationController @javax.inject.Inject() (
  val messagesApi: MessagesApi,
  override val tokenClient: io.flow.token.v0.interfaces.Client,
  override val dependencyClientProvider: DependencyClientProvider
) extends BaseController(tokenClient, dependencyClientProvider) {

  import scala.concurrent.ExecutionContext.Implicits.global
 
  override def section = Some(io.flow.dependency.www.lib.Section.Dashboard)

  def redirect = Action { request =>
    Redirect(request.path + "/")
  }

  def index(organization: Option[String], page: Int = 0) = Identified.async { implicit request =>
    for {
      recommendations <- dependencyClient(request).recommendations.get(
        organization = organization,
        limit = Pagination.DefaultLimit+1,
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
