package controllers

import db.{Authorization, InternalProjectLibrariesDao}
import io.flow.play.controllers.FlowControllerComponents
import io.flow.util.Config
import io.flow.dependency.v0.models.json._
import io.flow.postgresql.OrderBy
import lib.Conversions
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class ProjectLibraries @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents,
  projectLibrariesDao: InternalProjectLibrariesDao,
  conversions: Conversions,
) extends BaseIdentifiedControllerWithFallback {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    projectId: Option[String],
    libraryId: Option[String],
    isSynced: Option[Boolean],
    limit: Long = 25,
    offset: Long = 0,
  ): Action[AnyContent] = IdentifiedWithFallback { request =>
    Ok(
      Json.toJson(
        conversions.toProjectLibraryModels(
          projectLibrariesDao.findAll(
            Authorization.User(request.user.id),
            id = id,
            ids = optionals(ids),
            projectId = projectId,
            libraryId = libraryId,
            isSynced = isSynced,
            limit = Some(limit),
            offset = offset,
            orderBy = Some(OrderBy("created_at")),
          ),
        ),
      ),
    )
  }

}
