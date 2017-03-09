package controllers

import db.{LastEmailsDao, UsersDao}
import com.bryzek.dependency.v0.models.Publication
import com.bryzek.dependency.api.lib.{Email, Recipient}
import com.bryzek.dependency.actors._
import io.flow.play.controllers.FlowController
import io.flow.play.util.Config
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Emails @javax.inject.Inject() (
  val config: io.flow.play.util.Config,
) extends Controller with FlowController with Helpers {

  private[this] val TestEmailAddressName = "com.bryzek.dependency.api.test.email"
  private[this] lazy val TestEmailAddress = config.optionalString(TestEmailAddressName)

  override def user(
    session: play.api.mvc.Session,
    headers: play.api.mvc.Headers,
    path: String,
    queryString: Map[String, Seq[String]]
  ) (
    implicit ec: scala.concurrent.ExecutionContext
  ) = scala.concurrent.Future { None }

  def get() = Action { request =>
    TestEmailAddress match {
      case None => Ok(s"Set the $TestEmailAddressName property to enable testing")
      case Some(email) => {
        UsersDao.findByEmail("mbryzek@alum.mit.edu") match {
          case None => Ok(s"No user with email address[$email] found")
          case Some(user) => {
            val recipient = Recipient.fromUser(user).getOrElse {
              Recipient(email = "noemail@test.flow.io", name = user.name, userId = user.id, identifier = "TESTID")
            }
            val generator = DailySummaryEmailMessage(recipient)

            Ok(
              Seq(
                "Subject: " + Email.subjectWithPrefix(generator.subject()),
                "<br/><br/><hr size=1/>",
                generator.body()
              ).mkString("\n")
            ).as(HTML)
          }
        }
      }
    }
  }

}
