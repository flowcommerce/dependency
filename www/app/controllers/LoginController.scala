package controllers

import io.flow.common.v0.models.ExpandableUserDiscriminator.UserReference
import io.flow.dependency.v0.models.GithubAuthenticationForm
import io.flow.dependency.www.lib.{DependencyClientProvider, UiData}
import io.flow.play.controllers.IdentifiedCookie._
import play.api.i18n._
import play.api.mvc._

class LoginController @javax.inject.Inject()(
  val provider: DependencyClientProvider,
  val controllerComponents: ControllerComponents
) extends play.api.mvc.BaseController with I18nSupport {

  import scala.concurrent.ExecutionContext.Implicits.global

  def index(returnUrl: Option[String]) = Action { implicit request =>
    Ok(views.html.login.index(UiData(requestPath = request.path), returnUrl))
  }

  def githubCallback(
    code: String,
    state: Option[String],
    returnUrl: Option[String]
  ) = Action.async { implicit request =>
    provider.newClient(None).githubUsers.postGithub(
      GithubAuthenticationForm(
        code = code
      )
    ).map { user =>
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
    }.recover {
      case response: io.flow.dependency.v0.errors.GenericErrorsResponse => {
        Ok(views.html.login.index(UiData(requestPath = request.path), returnUrl, response.genericErrors.flatMap(_.messages)))
      }
    }
  }

}
