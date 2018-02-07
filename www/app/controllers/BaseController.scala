package controllers

import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.Client
import io.flow.dependency.v0.models.Organization
import io.flow.dependency.www.lib.{DependencyClientProvider, Section, UiData}
import io.flow.play.controllers.IdentifiedCookie._
import io.flow.play.controllers._
import io.flow.play.util.{AuthHeaders, Config}
import play.api.i18n._
import play.api.mvc._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class UserActionBuilder(
  val parser: BodyParser[AnyContent],
  onUnauthorized: RequestHeader => Result
)(
  implicit val executionContext: ExecutionContext
) extends ActionBuilder[IdentifiedRequest, AnyContent] {

  def invokeBlock[A](request: Request[A], block: (IdentifiedRequest[A]) => Future[Result]): Future[Result] =
    request.session.get(UserKey) match {
      case None => Future.successful(onUnauthorized(request))
      case Some(userId) =>
        val auth = AuthHeaders.user(UserReference(id = userId))
        block(new IdentifiedRequest(auth, request))
    }
}

abstract class BaseController(
  config: Config,
  dependencyClientProvider: DependencyClientProvider
)(implicit val ec: ExecutionContext) extends FlowController with I18nSupport {

  protected def onUnauthorized(requestHeader: RequestHeader): Result =
    Redirect(routes.LoginController.index(return_url = Some(requestHeader.path))).flashing("warning" -> "Please login")

  private lazy val UserActionBuilder =
    new UserActionBuilder(controllerComponents.parsers.default, onUnauthorized = onUnauthorized)
  protected def User = UserActionBuilder

  private[this] lazy val client = dependencyClientProvider.newClient(user = None, requestId = None)

  def section: Option[Section]

  def withOrganization[T](
    request: IdentifiedRequest[T],
    key: String
  ) (
    f: Organization => Future[Result]
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ) = {
    dependencyClient(request).organizations.get(key = Some(key), limit = 1).flatMap { organizations =>
      organizations.headOption match {
        case None => Future {
          Redirect(routes.ApplicationController.index()).flashing("warning" -> s"Organization not found")
        }
        case Some(org) => {
          f(org)
        }
      }
    }
  }

  def organizations[T](
    request: IdentifiedRequest[T]
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): Future[Seq[Organization]] = {
    dependencyClient(request).organizations.get(
      userId = Some(request.user.id),
      limit = 100
    )
  }

  def uiData[T](
    request: IdentifiedRequest[T]
  ) (
    implicit ec: ExecutionContext
  ): UiData = {
    val user = Await.result(
      client.users.get(id = Some(request.user.id)),
      Duration(1, "seconds")
    ).headOption

    UiData(
      requestPath = request.path,
      user = user,
      section = section,
      config = config
    )
  }

  def uiData[T](
    request: AnonymousRequest[T], userReferenceOption: Option[UserReference]
  ) (
    implicit ec: ExecutionContext
  ): UiData = {
    val user = userReferenceOption.flatMap { ref =>
      Await.result(
        client.users.get(id = Some(ref.id)),
        Duration(1, "seconds")
      ).headOption
    }

    UiData(
      requestPath = request.path,
      user = user,
      section = section,
      config = config
    )
  }

  def dependencyClient[T](request: IdentifiedRequest[T]): Client = {
    dependencyClientProvider.newClient(user = Some(request.user), requestId = Some(request.auth.requestId))
  }

}
