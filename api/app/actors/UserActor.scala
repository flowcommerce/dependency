package io.flow.dependency.actors

import javax.inject.Inject
import io.flow.dependency.v0.models.{Publication, SubscriptionForm}
import db.{OrganizationsDao, SubscriptionsDao, UserIdentifiersDao, UsersDao}
import akka.actor.Actor
import io.flow.akka.SafeReceive
import io.flow.log.RollbarLogger

object UserActor {

  trait Message

  object Messages {
    case class Created(userId: String) extends Message
  }

}

class UserActor @Inject()(
  organizationsDao: OrganizationsDao,
  userIdentifiersDao: UserIdentifiersDao,
  subscriptionsDao: SubscriptionsDao,
  usersDao: UsersDao,
  rollbar: RollbarLogger
) extends Actor {

  private[this] implicit val logger: RollbarLogger = rollbar.fingerprint(getClass.getName)

  def receive: Receive = SafeReceive.withLogUnhandled {

    case UserActor.Messages.Created(userId) =>
      usersDao.findById(userId).foreach { user =>
        organizationsDao.upsertForUser(user)

        // This method will force create an identifier
        userIdentifiersDao.latestForUser(usersDao.systemUser, user)

        // Subscribe the user automatically to key personalized emails.
        Seq(Publication.DailySummary).foreach { publication =>
          subscriptionsDao.upsertByUserIdAndPublication(
            usersDao.systemUser,
            SubscriptionForm(
              userId = user.id,
              publication = publication
            )
          )
        }
      }
  }

}
