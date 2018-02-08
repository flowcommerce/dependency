package controllers

import db.{Authorization, ResolversDao}
import io.flow.common.v0.models.UserReference
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.{Config, Validation}
import io.flow.dependency.v0.models.{Resolver, ResolverForm, Visibility}
import io.flow.dependency.v0.models.json._
import io.flow.error.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Resolvers @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  resolversDao: ResolversDao,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents
) extends BaseIdentifiedControllerWithFallback {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    organization: Option[String],
    visibility: Option[Visibility],
    limit: Long = 25,
    offset: Long = 0
  ) = IdentifiedWithFallback { request =>
    Ok(
      Json.toJson(
        resolversDao.findAll(
          Authorization.User(request.user.id),
          id = id,
          ids = optionals(ids),
          visibility = visibility,
          organization = organization,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = IdentifiedWithFallback { request =>
    withResolver(request.user, id) { resolver =>
      Ok(Json.toJson(resolver))
    }
  }

  def post() = IdentifiedWithFallback(parse.json) { request =>
    request.body.validate[ResolverForm] match {
      case e: JsError => {
        UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
      }
      case s: JsSuccess[ResolverForm] => {
        resolversDao.create(request.user, s.get) match {
          case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
          case Right(resolver) => Created(Json.toJson(resolver))
        }
      }
    }
  }

  def deleteById(id: String) = IdentifiedWithFallback { request =>
    withResolver(request.user, id) { resolver =>
      resolversDao.delete(request.user, resolver)
      NoContent
    }
  }

  def withResolver(user: UserReference, id: String)(
    f: Resolver => Result
  ): Result = {
    resolversDao.findById(Authorization.User(user.id), id) match {
      case None => {
        Results.NotFound
      }
      case Some(resolver) => {
        f(resolver)
      }
    }
  }

}
