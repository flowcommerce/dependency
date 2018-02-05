package io.flow.dependency.actors

import javax.inject.Inject

import io.flow.play.util.Config
import io.flow.postgresql.Pager
import io.flow.common.v0.models.User
import db._
import io.flow.dependency.v0.models.{Publication, Subscription}
import io.flow.dependency.lib.Urls
import io.flow.dependency.api.lib.{Email, Recipient}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import akka.actor.Actor

object EmailActor {

  object Messages {
    case object ProcessDailySummary
  }

  //TODO used for pattern matching, remove static injector later
  val PreferredHourToSendEst: Int = {
    val config = play.api.Play.current.injector.instanceOf[Config]
    val value = config.requiredString("io.flow.dependency.api.email.daily.summary.hour.est").toInt
    assert( value >= 0 && value < 23 )
    value
  }

}

class EmailActor @Inject()(
  subscriptionsDao: SubscriptionsDao,
  batchEmailProcessor: BatchEmailProcessor
) extends Actor with Util {

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

      batchEmailProcessor.process(
        Publication.DailySummary,
        Pager.create { offset =>
          subscriptionsDao.findAll(
            publication = Some(Publication.DailySummary),
            minHoursSinceLastEmail = Some(hours),
            minHoursSinceRegistration = Some(hours),
            offset = offset
          )
        }
      ) { recipient =>
        new DailySummaryEmailMessage(recipient)
      }
    }

  }

}

class BatchEmailProcessor @Inject()(
  usersDao: UsersDao,
  lastEmailsDao: LastEmailsDao,
  recommendationsDao: RecommendationsDao,
  userIdentifiersDao: UserIdentifiersDao,
  config: Config
) {

  lazy val SystemUser = usersDao.systemUser

  def process(
    publication: Publication,
    subscriptions: Iterator[Subscription]
  ) (
    generator: Recipient => EmailMessageGenerator
  ) {
    subscriptions.foreach { subscription =>
      usersDao.findById(subscription.user.id).foreach { user =>
        Recipient.fromUser(userIdentifiersDao, usersDao, user).map { new DailySummaryEmailMessage(_) }.map { generator =>
          // Record before send in case of crash - prevent loop of
          // emails.
          lastEmailsDao.record(
            SystemUser,
            LastEmailForm(
              userId = user.id,
              publication = publication
            )
          )

          Email.sendHtml(
            config = config,
            recipient = generator.recipient,
            subject = generator.subject(),
            body = generator.body(lastEmailsDao, recommendationsDao, config)
          )
        }
      }
    }
  }
}

trait EmailMessageGenerator {
  def recipient(): Recipient
  def subject(): String
  def body(lastEmailsDao: LastEmailsDao, recommendationsDao: RecommendationsDao, config: Config): String
}


/**
  * Class which generates email message
  */
class DailySummaryEmailMessage (
  val recipient: Recipient
) extends EmailMessageGenerator {

  private[this] val MaxRecommendations = 250

  private[this] def lastEmail(lastEmailsDao: LastEmailsDao) = lastEmailsDao.findByUserIdAndPublication(recipient.userId, Publication.DailySummary)

  override def subject() = "Daily Summary"

  override def body(lastEmailsDao: LastEmailsDao, recommendationsDao: RecommendationsDao, config: Config) = {
    val recommendations = recommendationsDao.findAll(
      Authorization.User(recipient.userId),
      limit = MaxRecommendations
    )

    val (newRecommendations, oldRecommendations) = lastEmail(lastEmailsDao) match {
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
      lastEmail = lastEmail(lastEmailsDao),
      urls = Urls(config)
    ).toString
  }

}
