package controllers

import db.{Authorization, ResolversDao}
import io.flow.play.controllers.{FlowController, FlowControllerComponents, IdentifiedRestController}
import io.flow.play.util.{Config, Validation}
import io.flow.dependency.v0.models.{Resolver, ResolverForm, Visibility}
import io.flow.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Resolvers @javax.inject.Inject() (
  tokenClient: io.flow.token.v0.interfaces.Client,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  resolversDao: ResolversDao
) extends FlowController with Helpers {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    organization: Option[String],
    visibility: Option[Visibility],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
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

  def getById(id: String) = Identified { request =>
    withResolver(request.user, id) { resolver =>
      Ok(Json.toJson(resolver))
    }
  }

  def post() = Identified(parse.json) { request =>
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

  def deleteById(id: String) = Identified { request =>
    withResolver(request.user, id) { resolver =>
      resolversDao.delete(request.user, resolver)
      NoContent
    }
  }

}
