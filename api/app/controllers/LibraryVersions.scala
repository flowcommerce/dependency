package controllers

import db.{Authorization, LibraryVersionsDao}
import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.models.LibraryVersion
import io.flow.dependency.v0.models.json._
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.Config
import play.api.libs.json._
import play.api.mvc._

@javax.inject.Singleton
class LibraryVersions @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  libraryVersionsDao: LibraryVersionsDao,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents,
) extends BaseIdentifiedControllerWithFallback {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    libraryId: Option[String],
    limit: Long = 25,
    offset: Long = 0,
  ) = IdentifiedWithFallback { request =>
    Ok(
      Json.toJson(
        libraryVersionsDao.findAll(
          Authorization.User(request.user.id),
          id = id,
          ids = optionals(ids),
          libraryId = libraryId,
          limit = Some(limit),
          offset = offset,
        ),
      ),
    )
  }

  def getById(id: String) = IdentifiedWithFallback { request =>
    withLibraryVersion(request.user, id) { library =>
      Ok(Json.toJson(library))
    }
  }

  def withLibraryVersion(user: UserReference, id: String)(
    f: LibraryVersion => Result,
  ): Result = {
    libraryVersionsDao.findById(
      Authorization.User(user.id),
      id,
    ) match {
      case None => {
        NotFound
      }
      case Some(libraryVersion) => {
        f(libraryVersion)
      }
    }
  }
}
