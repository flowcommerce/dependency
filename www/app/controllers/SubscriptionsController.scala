package controllers

import _root_.controllers.BaseController
import io.flow.common.v0.models.{User, UserReference}
import io.flow.dependency.v0.models.{Publication, SubscriptionForm}
import io.flow.dependency.www.lib.{DependencyClientProvider, UiData}
import io.flow.play.controllers.FlowControllerComponents
import io.flow.util.Config
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

object Subscriptions {

  case class UserPublication(publication: Publication, isSubscribed: Boolean) {
    val label: String = publication match {
      case Publication.DailySummary => "Email me a daily summary of dependencies to upgrade"
      case Publication.UNDEFINED(key) => key
    }
  }

}

class SubscriptionsController @javax.inject.Inject()(
  val dependencyClientProvider: DependencyClientProvider,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents
)(implicit ec: ExecutionContext) extends BaseController(config, dependencyClientProvider) {

  private[this] lazy val client = dependencyClientProvider.newClient(user = None, requestId = None)

  // needs to specify a section for BaseController
  override def section = None

  def index(): Action[AnyContent] = User.async { implicit request =>
    dependencyClientProvider.newClient(
      user = Some(request.user),
      requestId = None
    ).users.getIdentifierById(request.user.id).map { id =>
      Redirect(routes.SubscriptionsController.identifier(id.value))
    }
  }

  def identifier(identifier: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      users <- client.users.get(
        identifier = Some(identifier)
      )
      subscriptions <- dependencyClientProvider.newClient(
        user = users.headOption.map(u => UserReference(u.id)),
        requestId = None
      ).subscriptions.get(
        identifier = Some(identifier),
        limit = Publication.all.size.toLong + 1L
      )
    } yield {
      val userPublications = Publication.all.map { p =>
        Subscriptions.UserPublication(
          publication = p,
          isSubscribed = subscriptions.exists(_.publication == p)
        )
      }
      Ok(views.html.subscriptions.identifier(uiData(request, users.headOption), identifier, userPublications))
    }
  }

  def postToggle(identifier: String, publication: Publication): Action[AnyContent] = Action.async {
    client.users.get(identifier = Some(identifier)).flatMap { users =>
      users.headOption match {
        case None => Future {
          Redirect(routes.SubscriptionsController.index()).flashing("warning" -> "User could not be found")
        }
        case Some(user) => {
          val identifiedClient = dependencyClientProvider.newClient(
            user = Some(UserReference(user.id)),
            requestId = None
          )
          identifiedClient.subscriptions.get(
            identifier = Some(identifier),
            publication = Some(publication)
          ).flatMap { subscriptions =>
            subscriptions.headOption match {
              case None => {
                identifiedClient.subscriptions.post(
                  SubscriptionForm(
                    userId = user.id,
                    publication = publication
                  ),
                  identifier = Some(identifier)
                ).map { _ =>
                  Redirect(routes.SubscriptionsController.identifier(identifier)).flashing("success" -> "Subscription added")
                }
              }
              case Some(subscription) => {
                identifiedClient.subscriptions.deleteById(
                  subscription.id,
                  identifier = Some(identifier)
                ).map { _ =>
                  Redirect(routes.SubscriptionsController.identifier(identifier)).flashing("success" -> "Subscription removed")
                }
              }
            }
          }
        }
      }
    }
  }

  def uiData[T](request: Request[T], user: Option[User]): UiData = {
    UiData(
      requestPath = request.path,
      user = user,
      section = Some(io.flow.dependency.www.lib.Section.Subscriptions),
      config = config
    )
  }

}
