package controllers

import java.util.UUID

import _root_.util.{DependencySpec, MockDependencyClient}
import io.flow.common.v0.models.Name
import io.flow.dependency.v0.models.UserForm

class SubscriptionsSpec extends DependencySpec with MockDependencyClient {

  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] lazy val defaultUser1 = createUser()
  private[this] lazy val defaultUser1Identifier = createUserIdentifier(defaultUser1)
  private[this] val defaultSubscription1 = createSubscription(createSubscriptionForm(defaultUser1))
  private[this] lazy val defaultUser2 = createUser()
  private[this] lazy val defaultUser2Identifier = createUserIdentifier(defaultUser2)
  private[this] val defaultSubscription2 = createSubscription(createSubscriptionForm(defaultUser2))

  "GET /subscriptions by identifier" in {
    await(
      identifiedClient().subscriptions.get(identifier = Some(defaultUser1Identifier.value))
    ).map(_.id) must contain theSameElementsAs Seq(defaultSubscription1.id)

    await(
      identifiedClient().subscriptions.get(identifier = Some(defaultUser2Identifier.value))
    ).map(_.id) must contain theSameElementsAs Seq(defaultSubscription2.id)

    await(
      identifiedClient().users.get(id = Some(UUID.randomUUID.toString))
    ).map(_.id) must be(
      Nil
    )
  }

  "DELETE /subscriptions/:id by identifier" in {
    val user = createUser()
    val user1Identifier = createUserIdentifier(user)
    val subscription = createSubscription(createSubscriptionForm(user))

    await(
      identifiedClient().subscriptions.deleteById(id = subscription.id, identifier = Some(user1Identifier.value))
    )
    subscriptionsDao.findById(subscription.id) must be(empty)
  }
}
