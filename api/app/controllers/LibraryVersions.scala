package controllers

import db.{Authorization, LibraryVersionsDao}
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.common.v0.models.UserReference
import io.flow.play.util.{Config, Validation}
import io.flow.dependency.v0.models.LibraryVersion
import io.flow.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class LibraryVersions @javax.inject.Inject() (
  tokenClient: io.flow.token.v0.interfaces.Client,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents
) extends FlowController {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    libraryId: Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
    Ok(
      Json.toJson(
        LibraryVersionsDao.findAll(
          Authorization.User(request.user.id),
          id = id,
          ids = optionals(ids),
          libraryId = libraryId,
          limit = Some(limit),
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = Identified { request =>
    withLibraryVersion(request.user, id) { library =>
      Ok(Json.toJson(library))
    }
  }

  def withLibraryVersion(user: UserReference, id: String) (
    f: LibraryVersion => Result
  ): Result = {
    LibraryVersionsDao.findById(
      Authorization.User(user.id),
      id
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

