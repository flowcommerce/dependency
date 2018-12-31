package io.flow.dependency.api.lib

import io.flow.dependency.v0.models.{Credentials, CredentialsUndefinedType, UsernamePassword}
import org.htmlcleaner.HtmlCleaner
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.StringUtils
import org.apache.commons.text.StringEscapeUtils
import java.net.URL


import scala.util.{Failure, Success, Try}


/**
  * Accepts the URI of a resolver
  * (e.g. https://oss.sonatype.org/content/repositories/snapshots) and
  * parsers the contents in a list of files and
  * directories. Intentionally NOT recursive.
  */
object RemoteDirectory {

  case class Result(
    directories: Seq[String] = Nil,
    files: Seq[String] = Nil
  )

  def fetch(
    url: String,
    credentials: Option[Credentials] = None
  ) (
    filter: String => Boolean = { !_.startsWith(".") }
  ): Result = {
    val base = Result()
    val cleaner = new HtmlCleaner()

    val uc = (new URL(url)).openConnection()
    credentials.map { cred =>
      cred match {
        case UsernamePassword(username, password) =>{
          val userpass = username + ":" + password.getOrElse("")
          val basicAuth = "Basic " + new String(new Base64().encode(userpass.getBytes()))
          uc.setRequestProperty ("Authorization", basicAuth)
        }
        case CredentialsUndefinedType(_) => {
          // No-op
        }
      }
    }

    Try(cleaner.clean(uc.getInputStream())) match {
      case Failure(_) => {
        base
      }
      case Success(rootNode) => {
        rootNode.getElementsByName("a", true).foldLeft(base) { case (result, elem) =>
          Option(elem.getAttributeByName("href")) match {
            case None => {
              result
            }
            case Some(_) => {
              val text = StringEscapeUtils.unescapeHtml4(elem.getText.toString)
              filter(StringUtils.stripEnd(text, "/")) match {
                case false => {
                  result
                }
                case true => {
                  text.endsWith("/") match {
                    case true => result.copy(directories = result.directories ++ Seq(text))
                    case false => result.copy(files = result.files ++ Seq(text))
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
