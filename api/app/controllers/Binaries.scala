package controllers

import controllers.helpers.BinaryHelper
import db.BinariesDao
import io.flow.dependency.v0.models.BinaryForm
import io.flow.dependency.v0.models.json._
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.Validation
import io.flow.util.Config
import play.api.libs.json._
import io.flow.error.v0.models.json._
import play.api.mvc._

@javax.inject.Singleton
class Binaries @javax.inject.Inject()(
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  binariesDao: BinariesDao,
  binaryHelper: BinaryHelper,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents
) extends BaseIdentifiedControllerWithFallback {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    projectId: Option[String],
    name: Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = IdentifiedWithFallback {
    Ok(
      Json.toJson(
        binariesDao.findAll(
          id = id,
          ids = optionals(ids),
          projectId = projectId,
          name = name,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = IdentifiedWithFallback {
    binaryHelper.withBinary(id) { binary =>
      Ok(Json.toJson(binary))
    }
  }

  def post() = IdentifiedWithFallback(parse.json) { request =>
    request.body.validate[BinaryForm] match {
      case e: JsError => {
        UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
      }
      case s: JsSuccess[BinaryForm] => {
        val form = s.get
        binariesDao.create(request.user, form) match {
          case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
          case Right(binary) => Created(Json.toJson(binary))
        }
      }
    }
  }

  def deleteById(id: String) = IdentifiedWithFallback { request =>
    binaryHelper.withBinary(id) { binary =>
      binariesDao.delete(request.user, binary) match {
        case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
        case Right(_) => NoContent
      }
    }
  }

}
