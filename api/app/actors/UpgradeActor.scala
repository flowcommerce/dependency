package actors

import akka.actor.{Actor, Props}
import cats.effect.IO
import cats.implicits._
import io.flow.akka.SafeReceive
import lib.UpgradeService
import play.api.Logger

class UpgradeActor(upgradeService: UpgradeService)
    extends Actor {
  private def logInfo(msg: String): IO[Unit] = IO { Logger.info(msg) }

  private val pureReceive: UpgradeActor.Message => IO[Unit] = {
    case UpgradeActor.Message.UpgradeLibraries =>

      logInfo(s"$toString: Upgrading libraries") *>
        upgradeService.upgradeLibraries
  }

  override def receive: Receive = SafeReceive {
    case message: UpgradeActor.Message => pureReceive(message).unsafeRunSync()
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
