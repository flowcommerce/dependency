package controllers

import io.flow.dependency.www.lib.UiData
import io.flow.util.Config
import org.webjars.play.WebJarsUtil
import play.api.i18n._
import play.api.mvc._

class HealthchecksController @javax.inject.Inject() (
  config: Config,
  val controllerComponents: ControllerComponents,
  val webJarsUtil: WebJarsUtil,
) extends play.api.mvc.BaseController
  with I18nSupport {

  def index() = Action { implicit request =>
    Ok(
      views.html.healthchecks.index(
        UiData(requestPath = request.path, config = config, webJarsUtil = webJarsUtil),
        "healthy",
      ),
    )

  }

}
