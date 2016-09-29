package com.bryzek.dependency.www.lib

import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat

object DateHelper {

  private[this] val EasternTime = DateTimeZone.forID("America/New_York")

  private[this] val DefaultLabel = "N/A"

  def shortDate(
    dateTime: DateTime
  ): String = shortDateOption(Some(dateTime))

  def shortDateOption(
    dateTime: Option[DateTime],
    default: String = DefaultLabel
  ): String = {
    dateTime match {
      case None => default
      case Some(dt) => {
        DateTimeFormat.shortDate.withZone(EasternTime).print(dt)
      }
    }
  }

  def longDateTime(
    dateTime: DateTime
  ): String = longDateTimeOption(Some(dateTime))

  def longDateTimeOption(
    dateTime: Option[DateTime],
    default: String = DefaultLabel
  ): String = {
    dateTime match {
      case None => default
      case Some(dt) => {
        DateTimeFormat.longDateTime.withZone(EasternTime).print(dt)
      }
    }
  }

}
