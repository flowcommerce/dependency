package util

import io.flow.common.v0.models.{Environment, UserReference}
import io.flow.play.util.{AuthHeaders, FlowSession}
import io.flow.test.utils.{FlowMockClient, FlowPlaySpec}

trait MockDependencyClient
  extends FlowMockClient[
    io.flow.dependency.v0.Client,
    io.flow.dependency.v0.errors.GenericErrorResponse,
    io.flow.dependency.v0.errors.UnitResponse
  ] {
  self: FlowPlaySpec =>

  override def createAnonymousClient(baseUrl: String): io.flow.dependency.v0.Client =
    new io.flow.dependency.v0.Client(
      ws = wsClient,
      baseUrl = baseUrl
    )

  override def createIdentifiedClient(
    baseUrl: String,
    user: UserReference,
    org: Option[String],
    session: Option[FlowSession]
  ): io.flow.dependency.v0.Client = {
    val auth = org match {
      case None => AuthHeaders.user(user, session = session)
      case Some(o) => AuthHeaders.organization(user, o, environment = Environment.Sandbox, session = session)
    }

    new io.flow.dependency.v0.Client(
      ws = wsClient,
      baseUrl = baseUrl,
      defaultHeaders = authHeaders.headers(auth)
    )
  }
}
