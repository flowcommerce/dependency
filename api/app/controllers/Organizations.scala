package controllers

import controllers.helpers.{OrganizationsHelper, UsersHelper}
import db.OrganizationsDao
import io.flow.dependency.v0.models.OrganizationForm
import io.flow.dependency.v0.models.json._
import io.flow.error.v0.models.json._
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.Validation
import io.flow.util.Config
import play.api.libs.json._
import play.api.mvc._

class Organizations @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  organizationsDao: OrganizationsDao,
  organizationsHelper: OrganizationsHelper,
  usersHelper: UsersHelper,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents
) extends BaseIdentifiedControllerWithFallback
  with BaseIdentifiedController {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    userId: Option[String],
    key: Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = IdentifiedWithFallback { request =>
    Ok(
      Json.toJson(
        organizationsDao.findAll(
          authorization(request),
          id = id,
          ids = optionals(ids),
          userId = userId,
          key = key,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = IdentifiedWithFallback { request =>
    organizationsHelper.withOrganization(request.user, id) { organization =>
      Ok(Json.toJson(organization))
    }
  }

  def getUsersByUserId(userId: String) = IdentifiedWithFallback {
    usersHelper.withUser(userId) { user =>
      Ok(Json.toJson(organizationsDao.upsertForUser(user)))
    }
  }

  def post() = IdentifiedWithFallback(parse.json) { request =>
    request.body.validate[OrganizationForm] match {
      case e: JsError => {
        UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
      }
      case s: JsSuccess[OrganizationForm] => {
        organizationsDao.create(request.user, s.get) match {
          case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
          case Right(organization) => Created(Json.toJson(organization))
        }
      }
    }
  }

  def putById(id: String) = IdentifiedWithFallback(parse.json) { request =>
    organizationsHelper.withOrganization(request.user, id) { organization =>
      request.body.validate[OrganizationForm] match {
        case e: JsError => {
          UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
        }
        case s: JsSuccess[OrganizationForm] => {
          organizationsDao.update(request.user, organization, s.get) match {
            case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
            case Right(updated) => Ok(Json.toJson(updated))
          }
        }
      }
    }
  }

  def deleteById(id: String) = IdentifiedWithFallback { request =>
    organizationsHelper.withOrganization(request.user, id) { organization =>
      organizationsDao.delete(request.user, organization) match {
        case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
        case Right(_) => NoContent
      }
    }
  }
}
