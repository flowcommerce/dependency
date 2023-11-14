package controllers

import scala.annotation.nowarn
import controllers.util.SubscriptionActionBuilder
import db.SubscriptionsDao
import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.models.json._
import io.flow.dependency.v0.models.{Publication, Subscription, SubscriptionForm}
import io.flow.error.v0.models.json._
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.Validation
import io.flow.util.Config
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.ExecutionContext

@javax.inject.Singleton
class Subscriptions @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  subscriptionsDao: SubscriptionsDao,
  subscriptionIdentified: SubscriptionActionBuilder,
)(implicit val ec: ExecutionContext)
  extends FlowController {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    userId: Option[String], // TODO: Remove
    userIdentifier: Option[String],
    publication: Option[Publication],
    limit: Long = 25,
    offset: Long = 0,
  ) = subscriptionIdentified { request =>
    assert(
      userId.isEmpty,
      "user id parameter no longer supported",
    )
    Ok(
      Json.toJson(
        subscriptionsDao.findAll(
          id = id,
          ids = optionals(ids),
          userId = Some(request.user.id),
          identifier = userIdentifier,
          publication = publication,
          limit = limit,
          offset = offset,
        ),
      ),
    )
  }

  def getById(id: String) = subscriptionIdentified { request =>
    withSubscription(request.user, id) { subscription =>
      Ok(Json.toJson(subscription))
    }
  }

  @nowarn
  def post(identifier: Option[String]) = subscriptionIdentified(parse.json) { request =>
    request.body.validate[SubscriptionForm] match {
      case e: JsError => {
        UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
      }
      case s: JsSuccess[SubscriptionForm] => {
        val form = s.get
        subscriptionsDao.upsertByUserIdAndPublication(request.user, form) match {
          case Left(errors) => {
            UnprocessableEntity(Json.toJson(Validation.errors(errors)))
          }
          case Right(_) => {
            val sub = subscriptionsDao.findByUserIdAndPublication(form.userId, form.publication).getOrElse {
              sys.error("Failed to upsert subscription")
            }
            Created(Json.toJson(sub))
          }
        }
      }
    }
  }

  @nowarn
  def deleteById(id: String, userIdentifier: Option[String]) = subscriptionIdentified { request =>
    withSubscription(request.user, id) { subscription =>
      subscriptionsDao.delete(request.user, subscription)
      NoContent
    }
  }

  def withSubscription(user: UserReference, id: String)(
    f: Subscription => Result,
  ): Result = {
    subscriptionsDao.findById(id) match {
      case None => {
        NotFound
      }
      case Some(subscription) if subscription.user.id == user.id => {
        f(subscription)
      }
      case Some(_) => NotFound
    }
  }

}
