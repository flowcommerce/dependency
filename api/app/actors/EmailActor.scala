package com.bryzek.dependency.actors

import io.flow.play.util.Config
import io.flow.postgresql.Pager
import io.flow.common.v0.models.User
import db.{Authorization, LastEmail, LastEmailForm, LastEmailsDao, RecommendationsDao, SubscriptionsDao, UserIdentifiersDao, UsersDao}
import com.bryzek.dependency.v0.models.{Publication, Subscription}
import com.bryzek.dependency.lib.Urls
import com.bryzek.dependency.api.lib.{Email, Recipient}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import akka.actor.Actor

object EmailActor {

  object Messages {
    case object ProcessDailySummary
  }

  val PreferredHourToSendEst: Int = {
    val config = play.api.Play.current.injector.instanceOf[Config]
    val value = config.requiredString("com.bryzek.dependency.api.email.daily.summary.hour.est").toInt
    assert( value >= 0 && value < 23 )
    value
  }

}

class EmailActor extends Actor with Util {

  private[this] def currentHourEst(): Int = {
    (new DateTime()).toDateTime(DateTimeZone.forID("America/New_York")).getHourOfDay
  }

  def receive = {

    /**
      * Selects people to whom we delivery email by:
      * 
      *  If it is our preferred time to send (7am), filter by anybody
      *  who has been a member for at least 2 hours and who has not
      *  received an email in last 2 hours. We use 2 hours to catch up
      *  from emails sent the prior day late (at say 10am) to get them
      *  back on schedule, while making sure we don't send back to
      *  back emails
      * 
      *  Otherwise, filter by 26 hours to allow us to catch up on any
      *  missed emails
      */
    case m @ EmailActor.Messages.ProcessDailySummary => withErrorHandler(m) {
      val hoursForPreferredTime = 2
      val hours = currentHourEst match {
        case EmailActor.PreferredHourToSendEst => hoursForPreferredTime
        case _ => 24 + hoursForPreferredTime
      }

      BatchEmailProcessor(
        Publication.DailySummary,
        Pager.create { offset =>
          SubscriptionsDao.findAll(
            publication = Some(Publication.DailySummary),
            minHoursSinceLastEmail = Some(hours),
            minHoursSinceRegistration = Some(hours),
            offset = offset
          )
        }
      ) { recipient =>
        DailySummaryEmailMessage(recipient)
      }.process()
    }

  }

}

case class BatchEmailProcessor(
  publication: Publication,
  subscriptions: Iterator[Subscription]
) (
  generator: Recipient => EmailMessageGenerator
) {

  def process() {
    subscriptions.foreach { subscription =>
      UsersDao.findById(subscription.user.id).foreach { user =>
        Recipient.fromUser(user).map { DailySummaryEmailMessage(_) }.map { generator =>
          // Record before send in case of crash - prevent loop of
          // emails.
          LastEmailsDao.record(
            MainActor.SystemUser,
            LastEmailForm(
              userId = user.id,
              publication = publication
            )
          )

          Email.sendHtml(
            recipient = generator.recipient,
            subject = generator.subject(),
            body = generator.body()
          )
        }
      }
    }
  }
}

trait EmailMessageGenerator {
  def recipient(): Recipient
  def subject(): String
  def body(): String
}

/**
  * Class which generates email message
  */
case class DailySummaryEmailMessage(recipient: Recipient) extends EmailMessageGenerator {

  private[this] val MaxRecommendations = 250

  private[this] val lastEmail = LastEmailsDao.findByUserIdAndPublication(recipient.userId, Publication.DailySummary)

  private[this] lazy val config = play.api.Play.current.injector.instanceOf[Config]

  override def subject() = "Daily Summary"

  override def body() = {
    val recommendations = RecommendationsDao.findAll(
      Authorization.User(recipient.userId),
      limit = MaxRecommendations
    )

    val (newRecommendations, oldRecommendations) = lastEmail match {
      case None => (recommendations, Nil)
      case Some(email) => {
        (
          recommendations.filter { !_.createdAt.isBefore(email.createdAt) },
          recommendations.filter { _.createdAt.isBefore(email.createdAt) }
        )
      }
    }

    views.html.emails.dailySummary(
      recipient = recipient,
      newRecommendations = newRecommendations,
      oldRecommendations = oldRecommendations,
      lastEmail = lastEmail,
      urls = Urls(config)
    ).toString
  }

}
