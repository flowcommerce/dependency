package controllers

import io.flow.dependency.www.lib.UiData
import io.flow.util.Config
import play.api.i18n._
import play.api.mvc._

class HealthchecksController @javax.inject.Inject() (
  config: Config,
  val controllerComponents: ControllerComponents,
) extends play.api.mvc.BaseController
  with I18nSupport {

  def index() = Action { implicit request =>
    Ok(
      views.html.healthchecks.index(
        UiData(requestPath = request.path, config = config),
        "healthy",
      ),
    )

  }

}
