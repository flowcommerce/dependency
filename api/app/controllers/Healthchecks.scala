package controllers

import javax.inject.{Inject, Singleton}

import io.flow.healthcheck.v0.models.json._
import io.flow.healthcheck.v0.models.Healthcheck
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.Config
import play.api.mvc._
import play.api.libs.json._

@Singleton
class Healthchecks @Inject() (
  val controllerComponents: ControllerComponents
) extends BaseController {

  private val HealthyJson = Json.toJson(Healthcheck(status = "healthy"))

  def getHealthcheck() = Action { _ =>
    Ok(HealthyJson)
  }

}
