package db

import io.flow.dependency.v0.models.Publication
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class SubscriptionsDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  "upsert" in {
    val form = createSubscriptionForm()
    SubscriptionsDao.upsertByUserIdAndPublication(systemUser, form).right.get

    SubscriptionsDao.upsertByUserIdAndPublication(systemUser, form)
    val subscription = SubscriptionsDao.findByUserIdAndPublication(form.userId, form.publication).get

    val otherSubscription = createSubscription()
    subscription.id must not be(otherSubscription.id)
  }

  "findById" in {
    val subscription = createSubscription()
    SubscriptionsDao.findById(subscription.id).map(_.id) must be(
      Some(subscription.id)
    )

    SubscriptionsDao.findById(UUID.randomUUID.toString) must be(None)
  }

  "findByUserIdAndPublication" in {
    val subscription = createSubscription()
    SubscriptionsDao.findByUserIdAndPublication(subscription.user.id, subscription.publication).map(_.id) must be(
      Some(subscription.id)
    )

    SubscriptionsDao.findByUserIdAndPublication(UUID.randomUUID.toString, subscription.publication).map(_.id) must be(None)
    SubscriptionsDao.findByUserIdAndPublication(subscription.user.id, Publication.UNDEFINED("other")).map(_.id) must be(None)
  }

  "findAll by ids" in {
    // Create subscriptions for two different users so unique constraint on (user_id, publication) is not violated 
    val subscription1 = createSubscription(user = createUser())
    val subscription2 = createSubscription(user = createUser())

    SubscriptionsDao.findAll(ids = Some(Seq(subscription1.id, subscription2.id))).map(_.id) must be(
      Seq(subscription1.id, subscription2.id)
    )

    SubscriptionsDao.findAll(ids = Some(Nil)) must be(Nil)
    SubscriptionsDao.findAll(ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    SubscriptionsDao.findAll(ids = Some(Seq(subscription1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(subscription1.id))
  }

  "findAll by identifier" in {
    val user = createUser()
    val identifier = UserIdentifiersDao.latestForUser(systemUser, user).value
    val subscription = createSubscription(createSubscriptionForm(user = user))

    SubscriptionsDao.findAll(identifier = Some(identifier)).map(_.id) must be(Seq(subscription.id))
    SubscriptionsDao.findAll(identifier = Some(createTestKey())) must be(Nil)
  }

  "findAll by minHoursSinceLastEmail" in {
    val user = createUser()
    val subscription = createSubscription(
      createSubscriptionForm(user = user, publication = Publication.DailySummary)
    )

    SubscriptionsDao.findAll(
      id = Some(subscription.id),
      minHoursSinceLastEmail = Some(1)
    ).map(_.id) must be(Seq(subscription.id))

    createLastEmail(createLastEmailForm(user = user, publication = Publication.DailySummary))
    
    SubscriptionsDao.findAll(
      id = Some(subscription.id),
      minHoursSinceLastEmail = Some(1)
    ) must be(Nil)
  }

}
