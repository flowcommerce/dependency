package io.flow.dependency.api.lib

import db.{UserIdentifiersDao, UsersDao}
import io.flow.common.v0.models.{Name, User}

/**
  * Information we use to render email messages, including the links
  * to unsubscribe.
  */
case class Recipient(
  email: String,
  name: Name,
  userId: String,
  identifier: String
){
  def fullName(): Option[String] = {
    Seq(name.first, name.last).flatten.map(_.trim).filter(!_.isEmpty).toList match {
      case Nil => None
      case names => Some(names.mkString(" "))
    }
  }
}

object Recipient {

  def fromUser(userIdentifiersDao: UserIdentifiersDao, usersDao: UsersDao, user: User): Option[Recipient] = {
    user.email.map { email =>
      Recipient(
        email = email,
        name = user.name,
        userId = user.id,
        identifier = userIdentifiersDao.latestForUser(usersDao.systemUser, user).value
      )
    }
  }

}


