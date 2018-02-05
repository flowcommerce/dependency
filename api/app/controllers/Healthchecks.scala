package controllers

import io.flow.common.v0.models.json._
import io.flow.healthcheck.v0.models.Healthcheck
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.Config
import play.api._
import play.api.mvc._
import play.api.libs.json._

class Healthchecks (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents
) extends FlowController {

  private val HealthyJson = Json.toJson(Healthcheck(status = "healthy"))

  def getHealthcheck() = Action { request =>
    io.flow.dependency.actors.MainActor.ref
    Ok(HealthyJson)
  }

}
