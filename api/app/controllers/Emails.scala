package controllers

import db.{LastEmailsDao, RecommendationsDao, UserIdentifiersDao, UsersDao}
import io.flow.dependency.actors._
import io.flow.dependency.api.lib.{Email, Recipient}
import io.flow.play.util.Config
import play.api.mvc._

@javax.inject.Singleton
class Emails @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  usersDao: UsersDao,
  lastEmailsDao: LastEmailsDao,
  recommendationsDao: RecommendationsDao,
  userIdentifiersDao: UserIdentifiersDao
) extends BaseController {
  private val mikeEmail = "mbryzek@alum.mit.edu"

  private[this] val TestEmailAddressName = "io.flow.dependency.api.test.email"
  private[this] lazy val TestEmailAddress = config.optionalString(TestEmailAddressName)

  def get() = Action {
    TestEmailAddress match {
      case None => Ok(s"Set the $TestEmailAddressName property to enable testing")
      case Some(email) => {
        usersDao.findByEmail(mikeEmail) match {
          case None => Ok(s"No user with email address[$email] found")
          case Some(user) => {
            val recipient = userIdentifiersDao.recipientForUser(user).getOrElse {
              Recipient(email = "noemail@test.flow.io", name = user.name, userId = user.id, identifier = "TESTID")
            }
            val generator = new DailySummaryEmailMessage(recipient)

            Ok(
              Seq(
                "Subject: " + Email.subjectWithPrefix(config, generator.subject),
                "<br/><br/><hr size=1/>",
                generator.body(lastEmailsDao, recommendationsDao, config)
              ).mkString("\n")
            ).as(HTML)
          }
        }
      }
    }
  }

}
