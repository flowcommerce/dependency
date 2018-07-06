package io.flow.dependency.api.lib

import io.flow.common.v0.models.Name

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
  def fullName: Option[String] = {
    Seq(name.first, name.last).flatten.map(_.trim).filter(!_.isEmpty).toList match {
      case Nil => None
      case names => Some(names.mkString(" "))
    }
  }
}
