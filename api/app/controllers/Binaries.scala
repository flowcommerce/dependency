package controllers

import controllers.helpers.BinaryHelper
import db.{Authorization, BinariesDao, TokensDao, UsersDao}
import io.flow.dependency.v0.models.BinaryForm
import io.flow.dependency.v0.models.json._
import io.flow.play.controllers.{AuthorizationImpl, FlowController, FlowControllerComponents}
import io.flow.play.util.{Config, Validation}
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
  ) = IdentifiedWithFallback { request =>
    Ok(
      Json.toJson(
        binariesDao.findAll(
          Authorization.User(request.user.id),
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

  def getById(id: String) = IdentifiedWithFallback { request =>
    binaryHelper.withBinary(request.user, id) { binary =>
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
          case Left(errors) => UnprocessableEntity(Json.toJson(Seq(Validation.errors(errors))))
          case Right(binary) => Created(Json.toJson(binary))
        }
      }
    }
  }

  def deleteById(id: String) = IdentifiedWithFallback { request =>
    binaryHelper.withBinary(request.user, id) { binary =>
      binariesDao.delete(request.user, binary)
      NoContent
    }
  }

}
