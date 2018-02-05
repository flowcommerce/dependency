package util

import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.Client
import io.flow.play.util.{AuthHeaders, FlowSession}
import io.flow.test.utils.{FlowMockClient, FlowPlaySpec}

trait MockDependencyClient extends FlowMockClient[
  io.flow.dependency.v0.Client,
  io.flow.dependency.v0.errors.GenericErrorsResponse,
  io.flow.dependency.v0.errors.UnitResponse
  ]{
  self: FlowPlaySpec =>

  override def createAnonymousClient(baseUrl: String): Client =
    new io.flow.dependency.v0.Client(
      ws = wsClient,
      baseUrl = baseUrl
    )

  override def createIdentifiedClient(baseUrl: String, user: UserReference, org: Option[String], session: Option[FlowSession]): Client = {
    val auth = org match {
      case None =>  AuthHeaders.user(user, session = session)
      case Some(o) => AuthHeaders.organization(user, o, session = session)
    }

    new io.flow.dependency.v0.Client(
      ws = wsClient,
      baseUrl = baseUrl,
      defaultHeaders = authHeaders.headers(auth)
    )
  }
}
