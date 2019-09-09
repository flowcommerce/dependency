package io.flow.dependency.actors

import javax.inject.Inject
import db.{InternalTasksDao, SyncsDao}
import akka.actor.{Actor, ActorLogging, ActorSystem}
import io.flow.akka.SafeReceive
import io.flow.akka.recurring.{ScheduleConfig, Scheduler}
import io.flow.log.RollbarLogger
import io.flow.play.util.ApplicationConfig

import scala.concurrent.ExecutionContext

object PeriodicActor {

  sealed trait Message

  object Messages {
    case object Purge extends Message
    case object SyncBinaries extends Message
    case object SyncLibraries extends Message
    case object SyncProjects extends Message
  }

}

class PeriodicActor @Inject()(
  config: ApplicationConfig,
  system: ActorSystem,
  syncsDao: SyncsDao,
  internalTasksDao: InternalTasksDao,
  logger: RollbarLogger
) extends Actor with ActorLogging with Scheduler  {

  private[this] implicit val ec: ExecutionContext = system.dispatchers.lookup("periodic-actor-context")
  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)

  private[this] case object SyncAll
  private[this] case object Purge

  scheduleRecurring(
    ScheduleConfig.fromConfig(config.underlying.underlying, "io.flow.dependency.api.periodic.sync_all"),
    SyncAll
  )

  scheduleRecurring(
    ScheduleConfig.fromConfig(config.underlying.underlying, "io.flow.dependency.api.periodic.purge"),
    Purge
  )

  def receive: Receive = SafeReceive.withLogUnhandled {
    case Purge => syncsDao.purgeOld()
    case SyncAll => internalTasksDao.createSyncAllIfNotQueued()
  }

}
