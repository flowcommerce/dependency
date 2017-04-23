package controllers

import com.bryzek.dependency.v0.Client
import com.bryzek.dependency.v0.models.Organization
import com.bryzek.dependency.www.lib.{DependencyClientProvider, Section, UiData}
import io.flow.common.v0.models.{User, UserReference}
import io.flow.token.v0.interfaces.{Client => TokenClient}
import io.flow.play.controllers.FlowController
import scala.concurrent.{ExecutionContext, Future}
import play.api._
import play.api.i18n._
import play.api.mvc._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

object Helpers {

  def userFromSession(
    tokenClient: TokenClient,
    session: play.api.mvc.Session
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): scala.concurrent.Future[Option[UserReference]] = {
    session.get("user_id") match {
      case None => {
        Future { None }
      }

      case Some(userId) => {
        tokenClient.tokens.get(token = Some(userId)).map { result =>
          result.headOption.map(_.user)
        }
      }
    }
  }

}

abstract class BaseController(
  val tokenClient: io.flow.token.v0.interfaces.Client,
  val dependencyClientProvider: DependencyClientProvider
) extends Controller
    with FlowController
    with I18nSupport
{

  private[this] lazy val client = dependencyClientProvider.newClient(user = None)

  def section: Option[Section]

  override def unauthorized[A](request: Request[A]): Result = {
    Redirect(routes.LoginController.index(return_url = Some(request.path))).flashing("warning" -> "Please login")
  }

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

  override def user(
    session: play.api.mvc.Session,
    headers: play.api.mvc.Headers,
    path: String,
    queryString: Map[String, Seq[String]]
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ): scala.concurrent.Future[Option[UserReference]] = {
    Helpers.userFromSession(tokenClient, session)
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
      section = section
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
      section = section
    )
  }

  def dependencyClient[T](request: IdentifiedRequest[T]): Client = {
    dependencyClientProvider.newClient(user = Some(request.user))
  }

}
