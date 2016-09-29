package com.bryzek.dependency.www.lib

import io.flow.common.v0.models.UserReference
import com.bryzek.dependency.v0.{Authorization, Client}
import com.bryzek.dependency.v0.errors.UnitResponse
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait DependencyClientProvider {

  def newClient(user: Option[UserReference]): Client

}

@javax.inject.Singleton
class DefaultDependencyClientProvider @javax.inject.Inject() (
  config: io.flow.play.util.Config
) extends DependencyClientProvider {

  def host: String = config.requiredString("dependency.api.host")
  def token: String = config.requiredString("dependency.api.token")

  private[this] lazy val client = new Client(host)

  override def newClient(user: Option[UserReference]): Client = {
    user match {
      case None => {
        client
      }
      case Some(u) => {
        new Client(
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
