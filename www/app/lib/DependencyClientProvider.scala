package io.flow.dependency.www.lib

import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.{Authorization, Client}
import io.flow.play.util.AuthHeaders
import play.api.libs.ws.WSClient

trait DependencyClientProvider {

  def newClient(user: Option[UserReference], requestId: Option[String]): Client

}

@javax.inject.Singleton
class DefaultDependencyClientProvider @javax.inject.Inject() (
  config: io.flow.play.util.Config,
  wsClient: WSClient,
  authHeaders: AuthHeaders
) extends DependencyClientProvider {

  def host: String = config.requiredString("dependency.api.host")

  private[this] lazy val client = new Client(wsClient, host)

  override def newClient(user: Option[UserReference], requestId: Option[String]): Client = {
    user match {
      case None => {
        client
      }
      case Some(u) => {
        val authHeaderUser = requestId match {
          case Some(rid) => AuthHeaders.user(user = u, requestId = rid)
          case None => AuthHeaders.user(user = u)
        }

        new Client(
          ws = wsClient,
          baseUrl = host,
          auth = Some(
            Authorization.Basic(
              username = u.id.toString,
              password = None
            )
          ),
          defaultHeaders = authHeaders.headers(authHeaderUser)
        )
      }
    }
  }

}
