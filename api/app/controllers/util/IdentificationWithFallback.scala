package controllers.util

import java.util.UUID

import javax.inject.{Inject, Singleton}
import db.{TokensDao, UsersDao}
import io.flow.common.v0.models.UserReference
import io.flow.log.RollbarLogger
import io.flow.play.controllers._
import io.flow.play.util.{AuthData, Config}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

// Custom implementation based on FlowController.Identified but with database fallback for auth
@Singleton
class IdentificationWithFallback @Inject() (
  val parser: BodyParsers.Default,
  val config: Config,
  authorization: AuthorizationImpl,
  tokensDao: TokensDao,
  usersDao: UsersDao
)(implicit val executionContext: ExecutionContext, logger: RollbarLogger)
  extends ActionBuilder[IdentifiedRequest, AnyContent]
  with FlowActionInvokeBlockHelper {

  def invokeBlock[A](request: Request[A], block: (IdentifiedRequest[A]) => Future[Result]): Future[Result] = {
    auth(request.headers)(AuthData.Identified.fromMap) match {
      case None =>
        legacyUser(request.headers) match {
          case None => Future.successful(unauthorized(request))
          case Some(user) =>
            val ad = AuthData.Identified(
              user = user,
              session = None,
              requestId = "lib-play-" + UUID.randomUUID.toString,
              customer = None
            )
            block(new IdentifiedRequest(ad, request))
        }
      case Some(ad) => block(new IdentifiedRequest(ad, request))
    }
  }

  private[this] def legacyUser(headers: Headers): Option[UserReference] = {
    basicAuthorizationToken(headers) match {
      case None => None
      case Some(token) => {
        token match {
          case token: Token => getUser(token.token)
          case token: JwtToken => Some(UserReference(token.userId))
        }
      }
    }
  }

  private def getUser(token: String): Option[UserReference] = {
    tokensDao.findByToken(token) match {
      case Some(t) => Some(UserReference(t.user.id))
      case None => usersDao.findById(token).map(user => UserReference(user.id))
    }
  }

  /** If present, parses the basic authorization header and returns its decoded value.
    */
  private[this] def basicAuthorizationToken(
    headers: play.api.mvc.Headers
  ): Option[Authorization] = {
    headers.get("Authorization").flatMap { h =>
      authorization.get(h)
    }
  }

}
