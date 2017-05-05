package com.bryzek.dependency.actors

import com.bryzek.dependency.v0.models.{Publication, SubscriptionForm}
import io.flow.common.v0.models.User
import db.{OrganizationsDao, SubscriptionsDao, UserIdentifiersDao, UsersDao}
import akka.actor.Actor
import scala.concurrent.ExecutionContext

object UserActor {

  trait Message

  object Messages {
    case class Data(id: String) extends Message
    case object Created extends Message
  }

}

class UserActor extends Actor with Util {

  var dataUser: Option[User] = None

  def receive = {

    case m @ UserActor.Messages.Data(id) => withErrorHandler(m.toString) {
      dataUser = UsersDao.findById(id)
    }

    case m @ UserActor.Messages.Created => withErrorHandler(m.toString) {
      dataUser.foreach { user =>
        OrganizationsDao.upsertForUser(user)

        // This method will force create an identifier
        UserIdentifiersDao.latestForUser(MainActor.SystemUser, user)

        // Subscribe the user automatically to key personalized emails.
        Seq(Publication.DailySummary).foreach { publication =>
          SubscriptionsDao.upsertByUserIdAndPublication(
            MainActor.SystemUser,
            SubscriptionForm(
              userId = user.id,
              publication = publication
            )
          )
        }
      }
    }

    case m: Any => logUnhandledMessage(m)
  }

}
