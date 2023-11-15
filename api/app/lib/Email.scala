package io.flow.dependency.api.lib

import io.flow.common.v0.models.Name
import io.flow.util.{Config, IdGenerator}
import java.nio.file.{Path, Paths, Files}
import java.nio.charset.StandardCharsets
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import com.sendgrid._
import com.sendgrid.helpers.mail.objects._

object Email {

  private[this] def subjectPrefix(config: Config) = config.requiredString("mail.subject.prefix")

  def subjectWithPrefix(config: Config, subject: String): String = {
    subjectPrefix(config) + " " + subject
  }

  private[this] def fromEmail(config: Config) = config.requiredString("mail.default.from.email")

  def localDeliveryDir(config: Config): Option[Path] =
    config.optionalString("mail.local.delivery.dir").map(Paths.get(_))

  // Initialize sendgrid on startup to verify that all of our settings
  // are here. If using localDeliveryDir, set password to a test
  // string.
  private[this] def sendgrid(config: Config) = {
    localDeliveryDir(config) match {
      case None => new SendGrid(config.requiredString("sendgrid.api.key"))
      case Some(_) => new SendGrid("development")
    }
  }

  def sendHtml(
    config: Config,
    recipient: Recipient,
    subject: String,
    body: String,
  ): Unit = {
    val prefixedSubject = subjectWithPrefix(config, subject)

    val from = new Email(fromEmail(config))
    val to = recipient.fullName match {
      case Some(fn) => new Email(recipient.email, fn)
      case None => new Email(recipient.email)
    }
    val content = new Content("text/html", body)

    val mail = new com.sendgrid.helpers.mail.Mail(from, prefixedSubject, to, content)

    localDeliveryDir(config) match {
      case Some(dir) => {
        localDelivery(dir, recipient, prefixedSubject, body)
        ()
      }

      case None => {
        val request = new Request()
        request.setMethod(Method.POST)
        request.setEndpoint("mail/send")
        request.setBody(mail.build())
        val response = sendgrid(config).api(request)
        assert(
          response.getStatusCode() == 202,
          s"Error sending email. Expected statusCode[202] but got[${response.getStatusCode()}]",
        )
      }
    }
  }

  private[this] def fullName(name: Name): Option[String] = {
    Seq(name.first, name.last).flatten.toList match {
      case Nil => None
      case names => Some(names.mkString(" "))
    }
  }

  private[this] def localDelivery(dir: Path, to: Recipient, subject: String, body: String): String = {
    val timestamp = ISODateTimeFormat.dateTimeNoMillis.print(new DateTime())

    Files.createDirectories(dir)
    val target = Paths.get(dir.toString, timestamp + "-" + IdGenerator("eml").randomId() + ".html")
    val name = fullName(to.name) match {
      case None => to.email
      case Some(name) => s""""$name" <${to.email}">"""
    }

    val bytes = s"""<p>
To: $name<br/>
Subject: $subject
</p>
<hr size="1"/>

$body
""".getBytes(StandardCharsets.UTF_8)
    Files.write(target, bytes)

    println(s"email delivered locally to $target")
    s"local-delivery-to-$target"
  }

}
