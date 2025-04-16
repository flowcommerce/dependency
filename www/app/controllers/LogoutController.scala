package controllers

import io.flow.dependency.www.lib.UiData
import io.flow.play.util.Config
import org.webjars.play.WebJarsUtil
import play.api.i18n.I18nSupport
import play.api.mvc.ControllerComponents

class LogoutController @javax.inject.Inject() (
  config: Config,
  val controllerComponents: ControllerComponents,
  val webJarsUtil: WebJarsUtil,
) extends play.api.mvc.BaseController
  with I18nSupport {

  def logged_out = Action { implicit request =>
    Ok(
      views.html.logged_out(
        UiData(requestPath = request.path, config = config, webJarsUtil = webJarsUtil),
      ),
    )
  }

  def index() = Action {
    Redirect("/logged_out").withNewSession
  }

}
