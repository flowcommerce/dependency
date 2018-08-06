package controllers

import java.util.UUID

import _root_.util.{DependencySpec, MockDependencyClient}
import io.flow.common.v0.models.Name
import io.flow.dependency.v0.models.UserForm

class SubscriptionsSpec extends DependencySpec with MockDependencyClient {

  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] lazy val user1 = createUser()
  private[this] lazy val user1Identifier = createUserIdentifier(user1)
  private[this] val subscription1 = createSubscription(createSubscriptionForm(user1))
  private[this] lazy val user2 = createUser()
  private[this] lazy val user2Identifier = createUserIdentifier(user2)
  private[this] val subscription2 = createSubscription(createSubscriptionForm(user2))

  "GET /subscriptions by identifier" in {
    await(
      identifiedClient().subscriptions.get(identifier = Some(user1Identifier.value))
    ).map(_.id) must contain theSameElementsAs Seq(subscription1.id)

    await(
      identifiedClient().subscriptions.get(identifier = Some(user2Identifier.value))
    ).map(_.id) must contain theSameElementsAs Seq(subscription2.id)

    await(
      identifiedClient().users.get(id = Some(UUID.randomUUID.toString))
    ).map(_.id) must be(
      Nil
    )
  }
}
