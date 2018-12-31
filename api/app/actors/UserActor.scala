package io.flow.dependency.actors

import javax.inject.Inject
import io.flow.dependency.v0.models.{Publication, SubscriptionForm}
import io.flow.common.v0.models.User
import db.{OrganizationsDao, SubscriptionsDao, UserIdentifiersDao, UsersDao}
import akka.actor.Actor
import io.flow.akka.SafeReceive
import io.flow.log.RollbarLogger

object UserActor {

  trait Message

  object Messages {
    case class Data(id: String) extends Message
    case object Created extends Message
  }

}

class UserActor @Inject()(
  organizationsDao: OrganizationsDao,
  userIdentifiersDao: UserIdentifiersDao,
  subscriptionsDao: SubscriptionsDao,
  usersDao: UsersDao,
  logger: RollbarLogger
) extends Actor {

  private[this] implicit val configuredRollbar = logger.fingerprint("UserActor")

  var dataUser: Option[User] = None
  lazy val SystemUser = usersDao.systemUser

  def receive = SafeReceive.withLogUnhandled {

    case UserActor.Messages.Data(id) =>
      dataUser = usersDao.findById(id)

    case UserActor.Messages.Created =>
      dataUser.foreach { user =>
        organizationsDao.upsertForUser(user)

        // This method will force create an identifier
        userIdentifiersDao.latestForUser(SystemUser, user)

        // Subscribe the user automatically to key personalized emails.
        Seq(Publication.DailySummary).foreach { publication =>
          subscriptionsDao.upsertByUserIdAndPublication(
            SystemUser,
            SubscriptionForm(
              userId = user.id,
              publication = publication
            )
          )
        }
      }
  }

}
