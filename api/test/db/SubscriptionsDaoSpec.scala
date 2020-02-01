package db

import java.util.UUID

import io.flow.dependency.v0.models.Publication
import util.DependencySpec

class SubscriptionsDaoSpec extends DependencySpec {

  "upsert" in {
    val form = createSubscriptionForm()
    subscriptionsDao.upsertByUserIdAndPublication(systemUser, form).rightValue

    subscriptionsDao.upsertByUserIdAndPublication(systemUser, form)
    val subscription = subscriptionsDao.findByUserIdAndPublication(form.userId, form.publication).get

    val otherSubscription = createSubscription()
    subscription.id must not be (otherSubscription.id)
  }

  "findById" in {
    val subscription = createSubscription()
    subscriptionsDao.findById(subscription.id).map(_.id) must be(
      Some(subscription.id)
    )

    subscriptionsDao.findById(UUID.randomUUID.toString) must be(None)
  }

  "findByUserIdAndPublication" in {
    val subscription = createSubscription()
    subscriptionsDao.findByUserIdAndPublication(subscription.user.id, subscription.publication).map(_.id) must be(
      Some(subscription.id)
    )

    subscriptionsDao.findByUserIdAndPublication(UUID.randomUUID.toString, subscription.publication).map(_.id) must be(None)
    subscriptionsDao.findByUserIdAndPublication(subscription.user.id, Publication.UNDEFINED("other")).map(_.id) must be(None)
  }

  "findAll by ids" in {
    // Create subscriptions for two different users so unique constraint on (user_id, publication) is not violated 
    val subscription1 = createSubscription(user = createUser())
    val subscription2 = createSubscription(user = createUser())

    subscriptionsDao.findAll(ids = Some(Seq(subscription1.id, subscription2.id))).map(_.id) must be(
      Seq(subscription1.id, subscription2.id)
    )

    subscriptionsDao.findAll(ids = Some(Nil)) must be(Nil)
    subscriptionsDao.findAll(ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    subscriptionsDao.findAll(ids = Some(Seq(subscription1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(subscription1.id))
  }

  "findAll by identifier" in {
    val user = createUser()
    val identifier = userIdentifiersDao.latestForUser(systemUser, user).value
    val subscription = createSubscription(createSubscriptionForm(user = user))

    subscriptionsDao.findAll(identifier = Some(identifier)).map(_.id) must be(Seq(subscription.id))
    subscriptionsDao.findAll(identifier = Some(createTestKey())) must be(Nil)
  }

  "findAll by minHoursSinceLastEmail" in {
    val user = createUser()
    val subscription = createSubscription(
      createSubscriptionForm(user = user, publication = Publication.DailySummary)
    )

    subscriptionsDao.findAll(
      id = Some(subscription.id),
      minHoursSinceLastEmail = Some(1)
    ).map(_.id) must be(Seq(subscription.id))

    createLastEmail(createLastEmailForm(user = user, publication = Publication.DailySummary))

    subscriptionsDao.findAll(
      id = Some(subscription.id),
      minHoursSinceLastEmail = Some(1)
    ) must be(Nil)
  }

}
