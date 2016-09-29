package controllers

import db.{Authorization, RecommendationsDao}
import io.flow.play.controllers.IdentifiedRestController
import com.bryzek.dependency.v0.models.RecommendationType
import com.bryzek.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Recommendations @javax.inject.Inject() (
  override val config: io.flow.play.util.Config,
  override val tokenClient: io.flow.token.v0.interfaces.Client
) extends Controller with IdentifiedRestController with Helpers {

  def get(
    organization: Option[String],
    projectId: Option[String],
    `type`: Option[RecommendationType],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
    Ok(
      Json.toJson(
        RecommendationsDao.findAll(
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
