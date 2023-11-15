package controllers

import scala.annotation.nowarn
import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.models.GithubAuthenticationForm
import io.flow.dependency.www.lib
import io.flow.dependency.www.lib.{DependencyClientProvider, UiData}
import io.flow.play.controllers.IdentifiedCookie._
import io.flow.play.util.Config
import play.api.i18n._
import play.api.mvc._

import scala.concurrent.ExecutionContext

class LoginController @javax.inject.Inject() (
  val provider: DependencyClientProvider,
  val controllerComponents: ControllerComponents,
  config: Config,
)(implicit ec: ExecutionContext, dependencyConfig: lib.GitHubConfig)
  extends play.api.mvc.BaseController
  with I18nSupport {

  def index(returnUrl: Option[String]) = Action { implicit request =>
    Ok(views.html.login.index(UiData(requestPath = request.path, config = config), returnUrl))
  }

  @nowarn
  def githubCallback(
    code: String,
    state: Option[String],
    returnUrl: Option[String],
  ): Action[AnyContent] = Action.async { implicit request =>
    provider
      .newClient(user = None, requestId = None)
      .githubUsers
      .postGithub(
        GithubAuthenticationForm(
          code = code,
        ),
      )
      .map { user =>
        val url = returnUrl match {
          case None => {
            routes.ApplicationController.index().path
          }
          case Some(u) => {
            assert(u.startsWith("/"), s"Redirect URL[$u] must start with /")
            u
          }
        }
        Redirect(url).withIdentifiedCookieUser(user = UserReference(user.id))
      }
      .recover { case response: io.flow.dependency.v0.errors.GenericErrorResponse =>
        Ok(
          views.html.login
            .index(UiData(requestPath = request.path, config = config), returnUrl, response.genericError.messages),
        )
      }
  }

}
