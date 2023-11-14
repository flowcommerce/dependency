package io.flow.dependency.actors

import akka.actor.{ActorLogging, ActorSystem}
import db.{InternalTasksDao, SyncsDao}
import io.flow.akka.SafeReceive
import io.flow.akka.actor.ReapedActor
import io.flow.akka.recurring.{ScheduleConfig, Scheduler}
import io.flow.log.RollbarLogger
import io.flow.play.util.ApplicationConfig
import org.joda.time.DateTime

import javax.inject.Inject
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

class PeriodicActor @Inject() (
  config: ApplicationConfig,
  system: ActorSystem,
  syncsDao: SyncsDao,
  internalTasksDao: InternalTasksDao,
  logger: RollbarLogger,
) extends ReapedActor
  with ActorLogging
  with Scheduler
  with SchedulerCleanup {

  private[this] implicit val ec: ExecutionContext = system.dispatchers.lookup("periodic-actor-context")
  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)

  private[this] case object SyncAll
  private[this] case object Purge

  registerScheduledTask(
    scheduleRecurring(
      ScheduleConfig.fromConfig(config.underlying.underlying, "io.flow.dependency.api.periodic.sync_all"),
      SyncAll,
    ),
  )

  registerScheduledTask(
    scheduleRecurring(
      ScheduleConfig.fromConfig(config.underlying.underlying, "io.flow.dependency.api.periodic.purge"),
      Purge,
    ),
  )

  override def postStop(): Unit = try {
    cancelScheduledTasks()
  } finally {
    super.postStop()
  }

  def receive: Receive = SafeReceive.withLogUnhandled {
    case Purge => {
      internalTasksDao.deleteAllNonProcessedTasks(DateTime.now.minusHours(12))
      syncsDao.purgeOld()
    }
    case SyncAll => internalTasksDao.queueAll()
  }

}
