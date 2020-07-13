package controllers

import controllers.helpers.LibrariesHelper
import db.{Authorization, LibrariesDao}
import io.flow.dependency.v0.models.LibraryForm
import io.flow.dependency.v0.models.json._
import io.flow.error.v0.models.json._
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.{Config, Validation}
import play.api.libs.json._
import play.api.mvc._

@javax.inject.Singleton
class Libraries @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  librariesDao: LibrariesDao,
  librariesHelper: LibrariesHelper,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents
) extends BaseIdentifiedControllerWithFallback with BaseIdentifiedController {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    projectId: Option[String],
    groupId: Option[String],
    artifactId: Option[String],
    resolverId: Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = IdentifiedWithFallback { request =>
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
          limit = Some(limit),
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = IdentifiedWithFallback { request =>
    librariesHelper.withLibrary(request.user, id) { library =>
      Ok(Json.toJson(library))
    }
  }

  def post() = IdentifiedWithFallback(parse.json) { request =>
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

  def deleteById(id: String) = IdentifiedWithFallback { request =>
    librariesHelper.withLibrary(request.user, id) { library =>
      librariesDao.delete(request.user, library) match {
        case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
        case Right(_) => NoContent
      }
    }
  }

}
