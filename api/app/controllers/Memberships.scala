package controllers

import db.{Authorization, MembershipsDao}
import io.flow.play.controllers.FlowController
import io.flow.play.util.Validation
import io.flow.common.v0.models.UserReference
import com.bryzek.dependency.v0.models.{Membership, MembershipForm, Role}
import com.bryzek.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Memberships @javax.inject.Inject() (
  override val config: io.flow.play.util.Config,
) extends Controller with FlowController with Helpers {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    organization: Option[String],
    userId: Option[String],
    role: Option[Role],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
    Ok(
      Json.toJson(
        MembershipsDao.findAll(
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

  def getById(id: String) = Identified { request =>
    withMembership(request.user, id) { membership =>
      Ok(Json.toJson(membership))
    }
  }

  def post() = Identified(parse.json) { request =>
    request.body.validate[MembershipForm] match {
      case e: JsError => {
        UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
      }
      case s: JsSuccess[MembershipForm] => {
        MembershipsDao.create(request.user, s.get) match {
          case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
          case Right(membership) => Created(Json.toJson(membership))
        }
      }
    }
  }

  def deleteById(id: String) = Identified { request =>
    withMembership(request.user, id) { membership =>
      MembershipsDao.delete(request.user, membership)
      NoContent
    }
  }

  def withMembership(user: UserReference, id: String)(
    f: Membership => Result
  ): Result = {
    MembershipsDao.findById(Authorization.User(user.id), id) match {
      case None => {
        Results.NotFound
      }
      case Some(membership) => {
        f(membership)
      }
    }
  }

}
