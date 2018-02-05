package controllers

import controllers.helpers.{OrganizationsHelper, UsersHelper}
import db.OrganizationsDao
import io.flow.play.util.{Config, Validation}
import io.flow.dependency.v0.models.{Organization, OrganizationForm}
import io.flow.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import play.api.mvc._
import play.api.libs.json._

class Organizations @javax.inject.Inject()(
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  organizationsDao: OrganizationsDao,
  organizationsHelper: OrganizationsHelper,
  usersHelper: UsersHelper
) extends FlowController with BaseIdentifiedController {

  import scala.concurrent.ExecutionContext.Implicits.global

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    userId: Option[String],
    key: Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
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

  def getById(id: String) = Identified { request =>
    organizationsHelper.withOrganization(request.user, id) { organization =>
      Ok(Json.toJson(organization))
    }
  }

  def getUsersByUserId(userId: String) = Identified { request =>
    usersHelper.withUser(userId) { user =>
      Ok(Json.toJson(organizationsDao.upsertForUser(user)))
    }
  }

  def post() = Identified(parse.json) { request =>
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

  def putById(id: String) = Identified(parse.json) { request =>
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

  def deleteById(id: String) = Identified { request =>
    organizationsHelper.withOrganization(request.user, id) { organization =>
      organizationsDao.delete(request.user, organization)
      NoContent
    }
  }
}
