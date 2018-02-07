package io.flow.dependency.www.lib

import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.{Authorization, Client}
import io.flow.dependency.v0.errors.UnitResponse
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait DependencyClientProvider {

  def newClient(user: Option[UserReference]): Client

}

@javax.inject.Singleton
class DefaultDependencyClientProvider @javax.inject.Inject() (
  config: io.flow.play.util.Config,
  wsClient: WSClient
) extends DependencyClientProvider {

  def host: String = config.requiredString("dependency.api.host")

  private[this] lazy val client = new Client(wsClient, host)

  override def newClient(user: Option[UserReference]): Client = {
    user match {
      case None => {
        client
      }
      case Some(u) => {
        new Client(
          ws = wsClient,
          baseUrl = host,
          auth = Some(
            Authorization.Basic(
              username = u.id.toString,
              password = None
            )
          )
        )
      }
    }
  }


}
