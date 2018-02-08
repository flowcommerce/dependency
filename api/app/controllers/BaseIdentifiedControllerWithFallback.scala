package controllers

import javax.inject.Inject

import controllers.util.IdentificationWithFallback
import db.{TokensDao, UsersDao}
import io.flow.play.controllers.{AuthorizationImpl, FlowController}
import io.flow.play.util.Config
import play.api.mvc.BodyParsers

trait BaseIdentifiedControllerWithFallback extends FlowController {

  def config: Config
  def baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents

  private lazy val identifiedWithFallback: IdentificationWithFallback =
    new IdentificationWithFallback(
      baseIdentifiedControllerWithFallbackComponents.parser,
      config,
      baseIdentifiedControllerWithFallbackComponents.authorization,
      baseIdentifiedControllerWithFallbackComponents.tokensDao,
      baseIdentifiedControllerWithFallbackComponents.usersDao
    )(controllerComponents.executionContext)

  def IdentifiedWithFallback = identifiedWithFallback

}

case class BaseIdentifiedControllerWithFallbackComponents @Inject() (
  parser: BodyParsers.Default,
  authorization: AuthorizationImpl,
  tokensDao: TokensDao,
  usersDao: UsersDao
)
