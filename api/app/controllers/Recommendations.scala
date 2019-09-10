package controllers

import db.{Authorization, RecommendationsDao, TokensDao, StaticUserProvider}
import io.flow.dependency.v0.models.RecommendationType
import io.flow.dependency.v0.models.json._
import io.flow.play.controllers.{AuthorizationImpl, FlowControllerComponents}
import io.flow.play.util.Config
import play.api.libs.json._
import play.api.mvc._

@javax.inject.Singleton
class Recommendations @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  val authorization: AuthorizationImpl,
  val tokensDao: TokensDao,
  val staticUserProvider: StaticUserProvider,
  recommendationsDao: RecommendationsDao,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents
) extends BaseIdentifiedControllerWithFallback {

  def get(
    organization: Option[String],
    projectId: Option[String],
    `type`: Option[RecommendationType],
    limit: Long = 25,
    offset: Long = 0
  ) = IdentifiedWithFallback { request =>
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
