package actors

import akka.actor.{Actor, ActorLogging, Props}
import io.flow.akka.SafeReceive
import lib.UpgradeService

class UpgradeActor(upgradeService: UpgradeService) extends Actor with ActorLogging {
  override def receive: Receive = SafeReceive {
    case UpgradeActor.Message.UpgradeLibrary(artifactId) =>
      upgradeService.upgradeDependent(artifactId).unsafeRunSync()
  }
}

object UpgradeActor {
  sealed trait Message extends Product with Serializable
  object Message {
    case class UpgradeLibrary(artifactId: String) extends Message
  }

  val Name = "upgrade-actor"
  val Path = s"/user/$Name"

  def props(upgradeService: UpgradeService): Props =
    Props(new UpgradeActor(upgradeService))
}

