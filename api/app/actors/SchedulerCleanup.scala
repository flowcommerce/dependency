package io.flow.dependency.actors

import akka.actor.{Actor, Cancellable}

import scala.collection.mutable.{ListBuffer => MutableListBuffer}

trait SchedulerCleanup {
  this: Actor =>

  private[this] val cancellables = MutableListBuffer[Cancellable]()

  def registerScheduledTask(t: => Cancellable): Unit =
    cancellables.append(t)

  def cancelScheduledTasks(): Unit =
    cancellables.foreach(_.cancel())
}
