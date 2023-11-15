package controllers

import io.flow.dependency.v0.models.UserForm
import io.flow.dependency.v0.models.json._
import db.{UserIdentifiersDao, UsersDao}
import io.flow.common.v0.models.User
import io.flow.common.v0.models.json._
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.Validation
import io.flow.util.Config
import play.api.mvc._
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import io.flow.error.v0.models.json._

class Users @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents,
  usersDao: UsersDao,
  userIdentifiersDao: UserIdentifiersDao,
)(implicit val ec: ExecutionContext)
  extends BaseIdentifiedControllerWithFallback {

  def get(
    id: Option[String],
    email: Option[String],
    identifier: Option[String],
  ) = Anonymous { _ =>
    if (Seq(id, email, identifier).flatten.isEmpty) {
      UnprocessableEntity(Json.toJson(Validation.error("Must specify id, email or identifier")))
    } else {
      Ok(
        Json.toJson(
          usersDao.findAll(
            id = id,
            email = email,
            identifier = identifier,
            limit = 1,
            offset = 0,
          ),
        ),
      )
    }
  }

  def getById(id: String) = IdentifiedWithFallback {
    withUser(id) { user =>
      Ok(Json.toJson(user))
    }
  }

  def getIdentifierById(id: String) = IdentifiedWithFallback { request =>
    withUser(id) { user =>
      Ok(Json.toJson(userIdentifiersDao.latestForUser(request.user, user)))
    }
  }

  def post() = Anonymous.async(parse.json) { request =>
    request.body.validate[UserForm] match {
      case e: JsError =>
        Future {
          UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
        }
      case s: JsSuccess[UserForm] => {

        Future {
          usersDao.create(request.user, s.get) match {
            case Left(errors) => {
              UnprocessableEntity(Json.toJson(Validation.errors(errors)))
            }
            case Right(user) => {
              Created(Json.toJson(user))
            }
          }
        }
      }
    }
  }

  def withUser(id: String)(
    f: User => Result,
  ) = {
    usersDao.findById(id) match {
      case None => {
        NotFound
      }
      case Some(user) => {
        f(user)
      }
    }
  }

}
