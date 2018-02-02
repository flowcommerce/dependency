package controllers

import io.flow.common.v0.models.json._
import io.flow.healthcheck.v0.models.Healthcheck
import play.api._
import play.api.mvc._
import play.api.libs.json._

class Healthchecks extends Controller {

  private val HealthyJson = Json.toJson(Healthcheck(status = "healthy"))

  def getHealthcheck() = Action { request =>
    io.flow.dependency.actors.MainActor.ref
    Ok(HealthyJson)
  }

}
