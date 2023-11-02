package io.flow.dependency.actors

import akka.actor.{ActorLogging, ActorSystem}
import db._
import io.flow.akka.SafeReceive
import io.flow.akka.actor.ReapedActor
import io.flow.akka.recurring.{ScheduleConfig, Scheduler}
import io.flow.dependency.api.lib.{Email, Recipient}
import io.flow.dependency.lib.Urls
import io.flow.dependency.v0.models.{Publication, Subscription}
import io.flow.log.RollbarLogger
import io.flow.play.util.ApplicationConfig
import io.flow.postgresql.Pager
import io.flow.util.Config
import org.joda.time.{DateTime, DateTimeZone}

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class EmailActor @Inject() (
  system: ActorSystem,
  rollbar: RollbarLogger,
  config: ApplicationConfig,
  subscriptionsDao: SubscriptionsDao,
  batchEmailProcessor: BatchEmailProcessor
) extends ReapedActor
  with ActorLogging
  with Scheduler
  with SchedulerCleanup {

  private[this] implicit val ec: ExecutionContext = system.dispatchers.lookup("email-actor-context")

  private[this] case object ProcessDailySummary

  private[this] implicit val logger: RollbarLogger = rollbar.fingerprint(getClass.getName)

  registerScheduledTask(
    scheduleRecurring(
      ScheduleConfig.fromConfig(config.underlying.underlying, "io.flow.dependency.api.email"),
      ProcessDailySummary
    )
  )

  val PreferredHourToSendEst: Int = {
    val value = config.requiredString("io.flow.dependency.api.email.daily.summary.hour.est").toInt
    assert(value >= 0 && value < 23)
    value
  }

  private[this] def currentHourEst(): Int = {
    (new DateTime()).toDateTime(DateTimeZone.forID("America/New_York")).getHourOfDay
  }

  override def postStop(): Unit = try {
    cancelScheduledTasks()
  } finally {
    super.postStop()
  }

  def receive: Receive = SafeReceive.withLogUnhandled {

    /*
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
    case ProcessDailySummary =>
      val hoursForPreferredTime = 2
      val hours = currentHourEst() match {
        case PreferredHourToSendEst => hoursForPreferredTime
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
      )
  }
}

class BatchEmailProcessor @Inject() (
  usersDao: UsersDao,
  lastEmailsDao: LastEmailsDao,
  recommendationsDao: RecommendationsDao,
  userIdentifiersDao: UserIdentifiersDao,
  config: Config
) {

  def process(
    publication: Publication,
    subscriptions: Iterator[Subscription]
  ): Unit = {
    subscriptions.foreach { subscription =>
      usersDao
        .findById(subscription.user.id)
        .flatMap(userIdentifiersDao.recipientForUser)
        .foreach { recipient =>
          val generator = new DailySummaryEmailMessage(recipient)
          // Record before send in case of crash - prevent loop of
          // emails.
          lastEmailsDao.record(
            usersDao.systemUser,
            LastEmailForm(
              userId = recipient.userId,
              publication = publication
            )
          )

          Email.sendHtml(
            config = config,
            recipient = generator.recipient,
            subject = generator.subject,
            body = generator.body(lastEmailsDao, recommendationsDao, config)
          )
        }
    }
  }
}

trait EmailMessageGenerator {
  def recipient: Recipient
  def subject: String
  def body(lastEmailsDao: LastEmailsDao, recommendationsDao: RecommendationsDao, config: Config): String
}

/** Class which generates email message
  */
class DailySummaryEmailMessage(
  val recipient: Recipient
) extends EmailMessageGenerator {

  private[this] val MaxRecommendations = 250L

  private[this] def lastEmail(lastEmailsDao: LastEmailsDao): Option[LastEmail] =
    lastEmailsDao.findByUserIdAndPublication(recipient.userId, Publication.DailySummary)

  override def subject = "Daily Summary"

  override def body(lastEmailsDao: LastEmailsDao, recommendationsDao: RecommendationsDao, config: Config): String = {
    val recommendations = recommendationsDao.findAll(
      Authorization.User(recipient.userId),
      limit = MaxRecommendations
    )

    val (oldRecommendations, newRecommendations) = lastEmail(lastEmailsDao) match {
      case None => (Nil, recommendations)
      case Some(email) =>
        recommendations.partition(_.createdAt.isBefore(email.createdAt))
    }

    views.html.emails
      .dailySummary(
        recipient = recipient,
        newRecommendations = newRecommendations,
        oldRecommendations = oldRecommendations,
        lastEmail = lastEmail(lastEmailsDao),
        urls = Urls(config)
      )
      .toString
  }

}
