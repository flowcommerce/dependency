package controllers

import db.{Authorization, MembershipsDao}
import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.models.json._
import io.flow.dependency.v0.models.{Membership, MembershipForm, Role}
import io.flow.error.v0.models.json._
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.{Config, Validation}
import play.api.libs.json._
import play.api.mvc._

@javax.inject.Singleton
class Memberships @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  membershipsDao: MembershipsDao,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents
) extends BaseIdentifiedControllerWithFallback  {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    organization: Option[String],
    userId: Option[String],
    role: Option[Role],
    limit: Long = 25,
    offset: Long = 0
  ) = IdentifiedWithFallback { request =>
    Ok(
      Json.toJson(
        membershipsDao.findAll(
          Authorization.User(request.user.id),
          id = id,
          ids = optionals(ids),
          organization = organization,
          userId = userId,
          role = role,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = IdentifiedWithFallback { request =>
    withMembership(request.user, id) { membership =>
      Ok(Json.toJson(membership))
    }
  }

  def post() = IdentifiedWithFallback(parse.json) { request =>
    request.body.validate[MembershipForm] match {
      case e: JsError => {
        UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
      }
      case s: JsSuccess[MembershipForm] => {
        membershipsDao.create(request.user, s.get) match {
          case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
          case Right(membership) => Created(Json.toJson(membership))
        }
      }
    }
  }

  def deleteById(id: String) = IdentifiedWithFallback { request =>
    withMembership(request.user, id) { membership =>
      membershipsDao.delete(request.user, membership)
      NoContent
    }
  }

  def withMembership(user: UserReference, id: String)(
    f: Membership => Result
  ): Result = {
    membershipsDao.findById(Authorization.User(user.id), id) match {
      case None => {
        Results.NotFound
      }
      case Some(membership) => {
        f(membership)
      }
    }
  }

}
