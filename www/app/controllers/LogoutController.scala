package controllers

import io.flow.dependency.www.lib.UiData
import play.api.i18n.I18nSupport
import play.api.mvc.ControllerComponents

class LogoutController @javax.inject.Inject() (
  val controllerComponents: ControllerComponents
) extends play.api.mvc.BaseController with I18nSupport {

  def logged_out = Action { implicit request =>
    Ok(
      views.html.logged_out(
        UiData(requestPath = request.path)
      )
    )
  }

  def index() = Action {
    Redirect("/logged_out").withNewSession
  }


}
