package controllers

import db.{Authorization, ProjectLibrariesDao}
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.{Config, Validation}
import io.flow.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class ProjectLibraries @javax.inject.Inject() (
  tokenClient: io.flow.token.v0.interfaces.Client,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  projectLibrariesDao: ProjectLibrariesDao
) extends FlowController  {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    projectId: Option[String],
    libraryId: Option[String],
    isSynced: Option[Boolean],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
    Ok(
      Json.toJson(
        projectLibrariesDao.findAll(
          Authorization.User(request.user.id),
          id = id,
          ids = optionals(ids),
          projectId = projectId,
          libraryId = libraryId,
          isSynced = isSynced,
          limit = Some(limit),
          offset = offset
        )
      )
    )
  }

}
