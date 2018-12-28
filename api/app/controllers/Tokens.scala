package controllers

import db.{Authorization, InternalTokenForm, TokensDao}
import io.flow.play.util.Validation
import io.flow.util.Config
import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.models.{Token, TokenForm}
import io.flow.dependency.v0.models.json._
import io.flow.error.v0.models.json._
import io.flow.play.controllers.FlowControllerComponents
import play.api.mvc._
import play.api.libs.json._

class Tokens @javax.inject.Inject()(
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  tokensDao: TokensDao,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents
) extends BaseIdentifiedControllerWithFallback with BaseIdentifiedController {

  def get(
    ids: Option[Seq[String]],
    userId: Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = IdentifiedWithFallback { request =>
    Ok(
      Json.toJson(
        tokensDao.findAll(
          Authorization.User(request.user.id),
          ids = optionals(ids),
          userId = userId,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = IdentifiedWithFallback { request =>
    withUserCreatedToken(request.user, id) { token =>
      Ok(Json.toJson(tokensDao.addCleartextIfAvailable(request.user, token)))
    }
  }

  def post() = IdentifiedWithFallback(parse.json) { request =>
    request.body.validate[TokenForm] match {
      case e: JsError => {
        UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
      }
      case s: JsSuccess[TokenForm] => {
        tokensDao.create(request.user, InternalTokenForm.UserCreated(s.get)) match {
          case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
          case Right(token) => Created(Json.toJson(token))
        }
      }
    }
  }

  def deleteById(id: String) = IdentifiedWithFallback { request =>
    withUserCreatedToken(request.user, id) { token =>
      tokensDao.delete(request.user, token)
      NoContent
    }
  }

  def withUserCreatedToken(user: UserReference, id: String)(
    f: Token => Result
  ) = {
    tokensDao.findAll(Authorization.User(user.id), id = Some(id), tag = Some(InternalTokenForm.UserCreatedTag), limit = 1).headOption match {
      case None => {
        Results.NotFound
      }
      case Some(token) => {
        f(token)
      }
    }
  }

}
