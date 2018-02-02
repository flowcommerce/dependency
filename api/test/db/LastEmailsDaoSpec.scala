package db

import io.flow.dependency.v0.models.Publication
import io.flow.common.v0.models.User
import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatestplus.play._
import java.util.UUID

class LastEmailsDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  "delete" in {
    val lastEmail = createLastEmail()
    LastEmailsDao.delete(systemUser, lastEmail)
    LastEmailsDao.findById(lastEmail.id) must be(None)
  }

  "record" in {
    val form = createLastEmailForm()
    val lastEmail1 = createLastEmail(form)
    val lastEmail2 = createLastEmail(form)
    lastEmail1.id must not be(lastEmail2.id)

    LastEmailsDao.findById(lastEmail1.id) must be(None)
    LastEmailsDao.findById(lastEmail2.id).map(_.id) must be(Some(lastEmail2.id))
  }

  "findByUserIdAndPublication" in {
    val form = createLastEmailForm()
    val lastEmail = createLastEmail(form)

    LastEmailsDao.findByUserIdAndPublication(form.userId, form.publication).map(_.id) must be(Some(lastEmail.id))
    LastEmailsDao.findByUserIdAndPublication(UUID.randomUUID.toString, form.publication).map(_.id) must be(None)
    LastEmailsDao.findByUserIdAndPublication(form.userId, Publication.UNDEFINED("other")).map(_.id) must be(None)
  }

}
