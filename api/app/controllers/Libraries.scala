package controllers

import controllers.helpers.LibrariesHelper
import db.{Authorization, LibrariesDao}
import io.flow.dependency.v0.models.LibraryForm
import io.flow.dependency.v0.models.json._
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.{Config, Validation}
import play.api.libs.json._
import play.api.mvc._
import io.flow.error.v0.models.json._

@javax.inject.Singleton
class Libraries @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  librariesDao: LibrariesDao,
  librariesHelper: LibrariesHelper
) extends FlowController with BaseIdentifiedController {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    projectId: Option[String],
    groupId: Option[String],
    artifactId: Option[String],
    resolverId: Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
    Ok(
      Json.toJson(
        librariesDao.findAll(
          Authorization.User(request.user.id),
          id = id,
          ids = optionals(ids),
          projectId = projectId,
          groupId = groupId,
          artifactId = artifactId,
          resolverId = resolverId,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = Identified { request =>
    librariesHelper.withLibrary(request.user, id) { library =>
      Ok(Json.toJson(library))
    }
  }

  def post() = Identified(parse.json) { request =>
    request.body.validate[LibraryForm] match {
      case e: JsError => {
        UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
      }
      case s: JsSuccess[LibraryForm] => {
        librariesDao.create(request.user, s.get) match {
          case Left(errors) => UnprocessableEntity(Json.toJson(Seq(Validation.errors(errors))))
          case Right(library) => Created(Json.toJson(library))
        }
      }
    }
  }

  def deleteById(id: String) = Identified { request =>
    librariesHelper.withLibrary(request.user, id) { library =>
      librariesDao.delete(request.user, library)
      NoContent
    }
  }

}
