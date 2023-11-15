package controllers.util

import java.util.UUID

import db.{TokensDao, UsersDao}
import io.flow.common.v0.models.UserReference
import io.flow.log.RollbarLogger
import io.flow.play.controllers.{AuthorizationImpl, IdentifiedRequest}
import io.flow.play.util.{AuthData, Config}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@javax.inject.Singleton
class SubscriptionActionBuilder @javax.inject.Inject() (
  override val parser: BodyParsers.Default,
  override val config: Config,
  authorization: AuthorizationImpl,
  tokensDao: TokensDao,
  usersDao: UsersDao,
)(implicit override val executionContext: ExecutionContext, logger: RollbarLogger)
  extends IdentificationWithFallback(parser, config, authorization, tokensDao, usersDao) {

  override def invokeBlock[A](request: Request[A], block: IdentifiedRequest[A] => Future[Result]): Future[Result] = {
    request.queryString.getOrElse("identifier", Seq.empty).toList match {
      case Nil => {
        super.invokeBlock(request, block)
      }

      case identifier :: Nil => {
        usersDao.findByIdentifier(identifier) match {
          case None => {
            Future.successful(unauthorized(request))
          }
          case Some(user) => {
            val ad = AuthData.Identified(
              user = UserReference(id = user.id),
              session = None,
              requestId = "dependency-api-" + UUID.randomUUID.toString,
              customer = None,
            )
            block(new IdentifiedRequest(ad, request))
          }
        }
      }

      case multiple => {
        logger.withKeyValue("size", multiple.size).warn(s"Multiple identifiers found in request - assuming no User")
        Future.successful(unauthorized(request))
      }
    }
  }

}
