package controllers

import com.google.inject.Provider
import controllers.helpers.BinaryHelper
import db.{Authorization, BinariesDao}
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.{Config, Validation}
import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.models.{Binary, BinaryForm}
import io.flow.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Binaries @javax.inject.Inject()(
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  binariesDao: BinariesDao,
  binaryHelper: BinaryHelper
) extends FlowController  {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    projectId: Option[String],
    name: Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
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

  def getById(id: String) = Identified { request =>
    binaryHelper.withBinary(request.user, id) { binary =>
      Ok(Json.toJson(binary))
    }
  }

  def post() = Identified(parse.json) { request =>
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

  def deleteById(id: String) = Identified { request =>
    binaryHelper.withBinary(request.user, id) { binary =>
      binariesDao.delete(request.user, binary)
      NoContent
    }
  }

}
