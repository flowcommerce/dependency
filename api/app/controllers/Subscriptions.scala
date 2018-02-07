package controllers

import db.{SubscriptionsDao, UsersDao}
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.{Config, Validation}
import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.models.{Publication, Subscription, SubscriptionForm}
import io.flow.dependency.v0.models.json._
import io.flow.error.v0.models.json._
import play.api.Logger
import play.api.mvc._
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

@javax.inject.Singleton
class Subscriptions @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  usersDao: UsersDao,
  subscriptionsDao: SubscriptionsDao
) extends FlowController{

  /**
   * If we find an 'identifier' query string parameter, use that to
   * find the user and authenticate as that user.
   */
  def user(
    session: Session,
    headers: Headers,
    path: String,
    queryString: Map[String, Seq[String]]
  ) (
    implicit ec: ExecutionContext
  ): Future[Option[UserReference]] = {
    queryString.get("identifier").getOrElse(Nil).toList match {
      case Nil => {
        user(session, headers, path, queryString)
      }
      case id :: Nil => {
        Future {
          usersDao.findAll(identifier = Some(id), limit = 1).headOption.map { u => UserReference(id = u.id) }
        }
      }
      case multiple => {
        Logger.warn(s"Multiple identifiers[${multiple.size}] found in request - assuming no User")
        Future { None }
      }
    }
  }

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    userId: Option[String],
    identifier: Option[String],
    publication: Option[Publication],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
    Ok(
      Json.toJson(
        subscriptionsDao.findAll(
          id = id,
          ids = optionals(ids),
          userId = userId,
          identifier = identifier,
          publication = publication,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = Identified { request =>
    withSubscription(id) { subscription =>
      Ok(Json.toJson(subscription))
    }
  }

  def post(identifier: Option[String]) = Identified(parse.json) { request =>
    request.body.validate[SubscriptionForm] match {
      case e: JsError => {
        UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
      }
      case s: JsSuccess[SubscriptionForm] => {
        val form = s.get
        subscriptionsDao.upsertByUserIdAndPublication(request.user, form) match {
          case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
          case Right(subscription) => {
            val sub = subscriptionsDao.findByUserIdAndPublication(form.userId, form.publication).getOrElse {
              sys.error("Failed to upsert subscription")
            }
            Created(Json.toJson(sub))
          }
        }
      }
    }
  }

  def deleteById(id: String, identifier: Option[String]) = Identified { request =>
    withSubscription(id) { subscription =>
      subscriptionsDao.delete(request.user, subscription)
      NoContent
    }
  }

  def withSubscription(id: String)(
    f: Subscription => Result
  ): Result = {
    subscriptionsDao.findById(id) match {
      case None => {
        NotFound
      }
      case Some(subscription) => {
        f(subscription)
      }
    }
  }

}
