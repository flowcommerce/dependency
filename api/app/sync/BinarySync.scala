package sync

import db.{BinariesDao, BinaryVersionsDao, SyncsDao}
import io.flow.common.v0.models.UserReference
import io.flow.dependency.actors.SearchActor
import io.flow.dependency.api.lib.DefaultBinaryVersionProvider
import io.flow.dependency.v0.models.Binary
import io.flow.log.RollbarLogger
import io.flow.postgresql.Pager
import javax.inject.Inject

class BinarySync @Inject() (
  binariesDao: BinariesDao,
  defaultBinaryVersionProvider: DefaultBinaryVersionProvider,
  binaryVersionsDao: BinaryVersionsDao,
  syncsDao: SyncsDao,
  @javax.inject.Named("search-actor") searchActor: akka.actor.ActorRef,
  logger: RollbarLogger,
) {

  def sync(user: UserReference, binaryId: String): Unit = {
    binariesDao.findById(binaryId).foreach { binary =>
      syncsDao.withStartedAndCompleted("binary", binary.id) {
        val versions = defaultBinaryVersionProvider.versions(binary.name)
        logger
          .fingerprint(s"${getClass.getName}:results")
          .withKeyValue("binary", binary.toString)
          .withKeyValue("versions", versions.toString)
          .info("result")

        versions.foreach { version =>
          binaryVersionsDao.upsert(user, binary.id, version.value)
        }
      }
    }
    searchActor ! SearchActor.Messages.SyncBinary(binaryId)
  }
  def iterateAll()(f: Binary => Any): Unit = {
    Pager
      .create { offset =>
        binariesDao.findAll(offset = offset, limit = 1000)
      }
      .foreach { rec =>
        f(rec)
      }
  }
}
