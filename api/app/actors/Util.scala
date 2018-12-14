package io.flow.dependency.actors

import io.flow.log.RollbarLogger

/**
  * TODO: Extract to lib. Maybe lib-play-actors ??
  */
trait Util {

  val logger: RollbarLogger

  def withErrorHandler[T](
    description: Any
  ) (
    f: => T
  ) {
    try {
      f
    } catch {
      case t: Throwable => {
        logger.error(msg(description.toString) , t)
      }
    }
  }

  def withVerboseErrorHandler[T](
    description: Any
  ) (
    f: => T
  ) {
    logger.info(msg(description.toString))
    withErrorHandler(description)(f)
  }

  def logUnhandledMessage[T](
    description: Any
  ) {
    logger.withKeyValue("description", description.toString).error(msg(s"got an unhandled message"))
  }

  private[this] def msg(value: String) = {
    s"${getClass.getName}: $value"
  }

}
