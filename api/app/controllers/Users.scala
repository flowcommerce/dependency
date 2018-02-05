package controllers

import io.flow.dependency.v0.models.UserForm
import io.flow.dependency.v0.models.json._
import db.{UserIdentifiersDao, UsersDao}
import io.flow.common.v0.models.{User}
import io.flow.common.v0.models.json._
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.{Config, Validation}
import play.api.mvc._
import play.api.libs.json._

import scala.concurrent.Future

class Users @javax.inject.Inject()(
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  usersDao: UsersDao,
  userIdentifiersDao: UserIdentifiersDao
) extends FlowController {

  import scala.concurrent.ExecutionContext.Implicits.global

  def get(
    id: Option[String],
    email: Option[String],
    identifier: Option[String]
  ) = Anonymous { request =>
    if (Seq(id, email, identifier).isEmpty) {
      UnprocessableEntity(Json.toJson(Validation.error("Must specify id, email or identifier")))
    } else {
      Ok(
        Json.toJson(
          usersDao.findAll(
            id = id,
            email = email,
            identifier = identifier,
            limit = 1,
            offset = 0
          )
        )
      )
    }
  }

  def getById(id: String) = Identified { request =>
    withUser(id) { user =>
      Ok(Json.toJson(user))
    }
  }

  def getIdentifierById(id: String) = Identified { request =>
    withUser(id) { user =>
      Ok(Json.toJson(userIdentifiersDao.latestForUser(request.user, user)))
    }
  }

  def post() = Anonymous.async(parse.json) { request =>
    Future {
      request.body.validate[UserForm] match {
        case e: JsError =>
          UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
        case s: JsSuccess[UserForm] => {
          request.user.map { userOption =>
            usersDao.create(Option(userOption), s.get) match {
              case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
              case Right(user) => Created(Json.toJson(user))
            }
          }.getOrElse(UnprocessableEntity("no user on request")) //todo: not sure how this worked before
        }
      }
    }
  }

  def withUser(id: String)(
    f: User => Result
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
