package controllers

import javax.inject.Inject

import controllers.util.IdentificationWithFallback
import db.{TokensDao, UsersDao}
import io.flow.play.controllers.{AuthorizationImpl, FlowController}
import io.flow.play.util.Config

trait BaseIdentifiedControllerWithFallback extends FlowController {

  def config: Config
  def baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents

  private lazy val identifiedWithFallback: IdentificationWithFallback =
    new IdentificationWithFallback(
      controllerComponents.parsers.default,
      config,
      baseIdentifiedControllerWithFallbackComponents.authorization,
      baseIdentifiedControllerWithFallbackComponents.tokensDao,
      baseIdentifiedControllerWithFallbackComponents.usersDao
    )(controllerComponents.executionContext)

  def IdentifiedWithFallback = identifiedWithFallback

}

case class BaseIdentifiedControllerWithFallbackComponents @Inject() (
  authorization: AuthorizationImpl,
  tokensDao: TokensDao,
  usersDao: UsersDao
)
