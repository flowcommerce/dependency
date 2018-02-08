package controllers

import db.{Authorization, ProjectBinariesDao}
import io.flow.dependency.v0.models.json._
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.Config
import play.api.libs.json._
import play.api.mvc._

@javax.inject.Singleton
class ProjectBinaries @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  projectBinariesDao: ProjectBinariesDao,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents
) extends BaseIdentifiedControllerWithFallback  {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    projectId: Option[String],
    binaryId: Option[String],
    isSynced: Option[Boolean],
    limit: Long = 25,
    offset: Long = 0
  ) = IdentifiedWithFallback { request =>
    Ok(
      Json.toJson(
        projectBinariesDao.findAll(
          Authorization.User(request.user.id),
          id = id,
          ids = optionals(ids),
          projectId = projectId,
          binaryId = binaryId,
          isSynced = isSynced,
          limit = limit,
          offset = offset
        )
      )
    )
  }

}
