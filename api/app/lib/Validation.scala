package io.flow.dependency.api.lib

import scala.util.{Failure, Success, Try}
import java.net.URI

object Validation {

  def validateUri(uri: String): Either[Seq[String], URI] = {
    uri.trim match {
      case "" => Left(Seq(s"URI cannot be an empty string"))
      case trimmed => {
        if (trimmed.toLowerCase.startsWith("http")) {
          Try(URI.create(trimmed)) match {
            case Failure(error) => Left(Seq(s"Could not parse uri[$trimmed]: $error"))
            case Success(u) => Right(u)
          }
        } else {
          Left(Seq("URI must start with http"))
        }
      }
    }
  }

}
