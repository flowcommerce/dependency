package actors

import akka.actor.{Actor, Props}
import io.flow.akka.SafeReceive
import lib.UpgradeService
import play.api.Logger

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class UpgradeActor(upgradeService: UpgradeService)
    extends Actor {

  private val timeout: FiniteDuration = 5.minutes

  private def logInfo(msg: String): Unit = Logger.info(msg)

  private val asyncReceive: UpgradeActor.Message => Future[Unit] = {
    case UpgradeActor.Message.UpgradeLibraries =>

      logInfo(s"$toString: Upgrading libraries")
      upgradeService.upgradeLibraries()
  }

  override def receive: Receive = SafeReceive {
    case message: UpgradeActor.Message =>
      Await.result(
        asyncReceive(message),
        timeout
      )
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
