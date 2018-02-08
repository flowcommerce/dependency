package controllers.util

import java.util.UUID

import db.{TokensDao, UsersDao}
import io.flow.common.v0.models.UserReference
import io.flow.play.controllers.{AuthorizationImpl, IdentifiedRequest}
import io.flow.play.util.{AuthData, Config}
import play.api.Logger
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@javax.inject.Singleton
class SubscriptionActionBuilder @javax.inject.Inject() (
  override val parser: BodyParsers.Default,
  override val config: Config,
  authorization: AuthorizationImpl,
  tokensDao: TokensDao,
  usersDao: UsersDao
)(implicit override val executionContext: ExecutionContext) extends IdentificationWithFallback(parser, config, authorization, tokensDao, usersDao) {

  override def invokeBlock[A](request: Request[A], block: (IdentifiedRequest[A]) => Future[Result]): Future[Result] = {
    val identifiers = request.queryString.getOrElse("identifier", Seq.empty)
    if (identifiers.isEmpty) super.invokeBlock(request, block)
    else if (identifiers.size > 1) {
      Logger.warn(s"Multiple identifiers[${identifiers.size}] found in request - assuming no User")
      Future.successful(unauthorized(request))
    } else {
      usersDao.findById(identifiers.head) match {
        case None => Future.successful(unauthorized(request))
        case Some(user) =>
          val userRef = UserReference(id = user.id)
          val ad = AuthData.Identified(user = userRef, session = None, requestId = "lib-play-" + UUID.randomUUID.toString)
          block(new IdentifiedRequest(ad, request))
      }
    }
  }

}
