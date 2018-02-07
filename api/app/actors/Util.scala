package io.flow.dependency.actors

import play.api.Logger

/**
  * TODO: Extract to lib. Maybe lib-play-actors ??
  */
trait Util {

  def withErrorHandler[T](
    description: Any
  ) (
    f: => T
  ) {
    try {
      f
    } catch {
      case t: Throwable => {
        Logger.error(msg(s"$description: ${t}") , t)
      }
    }
  }

  def withVerboseErrorHandler[T](
    description: Any
  ) (
    f: => T
  ) {
    Logger.info(msg(description.toString))
    withErrorHandler(description)(f)
  }

  def logUnhandledMessage[T](
    description: Any
  ) {
    Logger.error(msg(s"got an unhandled message: $description"))
  }

private[this] def msg(value: String) = {
    s"${getClass.getName}: $value"
  }

}
