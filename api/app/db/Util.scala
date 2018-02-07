package db

import io.flow.dependency.v0.models.{Credentials, CredentialsUndefinedType, UsernamePassword}

object Util {

  def trimmedString(value: Option[String]): Option[String] = {
    value match {
      case None => None
      case Some(v) => {
        v.trim match {
          case "" => None
          case trimmed => Some(trimmed)
        }
      }
    }
  }

  private[db] def maskCredentials(cred: Credentials): Option[Credentials] = {
    cred match {
      case CredentialsUndefinedType(value) => None
      case UsernamePassword(username, password) => {
        Some(
          UsernamePassword(
            username,
            password = password.map(_ => "masked")
          )
        )
      }
    }
  }
}
