package controllers

import db.{Authorization, RecommendationsDao}
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.dependency.v0.models.RecommendationType
import io.flow.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import io.flow.play.util.Config
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Recommendations @javax.inject.Inject() (
  tokenClient: io.flow.token.v0.interfaces.Client,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  recommendationsDao: RecommendationsDao
) extends FlowController with Helpers {

  def get(
    organization: Option[String],
    projectId: Option[String],
    `type`: Option[RecommendationType],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
    Ok(
      Json.toJson(
        recommendationsDao.findAll(
          Authorization.User(request.user.id),
          organization = organization,
          projectId = projectId,
          `type` = `type`,
          limit = limit,
          offset = offset
        )
      )
    )
  }

}
