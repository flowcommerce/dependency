package actors

import akka.actor.{Actor, ActorLogging, Props}
import cats.effect.IO
import io.flow.akka.SafeReceive
import lib.UpgradeService

class UpgradeActor(upgradeService: UpgradeService)
    extends Actor
    with ActorLogging {

  private val pureReceive: PartialFunction[Any, IO[Unit]] = {
    case UpgradeActor.Message.UpgradeLibraries =>
      upgradeService.upgradeLibraries
  }

  override def receive: Receive = SafeReceive {
    pureReceive.andThen(_.unsafeRunSync())
  }
}

object UpgradeActor {
  sealed trait Message extends Product with Serializable
  object Message {
    case object UpgradeLibraries extends Message
  }

  val Name = "upgrade-actor"
  val Path = s"/user/$Name"

  def props(upgradeService: UpgradeService): Props =
    Props(new UpgradeActor(upgradeService))
}
