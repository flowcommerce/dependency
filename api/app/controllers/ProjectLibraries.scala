package controllers

import db.{Authorization, ProjectLibrariesDao}
import io.flow.play.controllers.FlowController
import io.flow.play.util.Validation
import com.bryzek.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class ProjectLibraries @javax.inject.Inject() (
  override val config: io.flow.play.util.Config,
) extends Controller with FlowController with Helpers {

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
        ProjectLibrariesDao.findAll(
          Authorization.User(request.user.id),
          id = id,
          ids = optionals(ids),
          projectId = projectId,
          libraryId = libraryId,
          isSynced = isSynced,
          limit = limit,
          offset = offset
        )
      )
    )
  }

}
