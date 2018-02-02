package db

import io.flow.dependency.v0.models.SyncEvent
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class SyncsDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val org = createOrganization()

  "create" in {
    val form = createSyncForm()
    val sync = SyncsDao.create(systemUser, form)

    sync.event must be(form.event)
  }

  "withStartedAndCompleted" in {
    val project = createProject(org)
    SyncsDao.withStartedAndCompleted(systemUser, "project", project.id) {
      // NO-OP
    }
    val events = SyncsDao.findAll(objectId = Some(project.id)).map(_.event)
    events.contains(SyncEvent.Started) must be(true)
    events.contains(SyncEvent.Completed) must be(true)
  }

  "recordStarted" in {
    val project = createProject(org)
    SyncsDao.recordStarted(systemUser, "project", project.id)
    SyncsDao.findAll(objectId = Some(project.id)).map(_.event).contains(SyncEvent.Started) must be(true)
  }

  "recordCompleted" in {
    val project = createProject(org)
    SyncsDao.recordCompleted(systemUser, "project", project.id)
    SyncsDao.findAll(objectId = Some(project.id)).map(_.event).contains(SyncEvent.Completed) must be(true)
  }

  "findById" in {
    val sync = createSync()
    SyncsDao.findById(sync.id).map(_.id) must be(
      Some(sync.id)
    )

    SyncsDao.findById(UUID.randomUUID.toString) must be(None)
  }

  "findAll by ids" in {
    val sync1 = createSync()
    val sync2 = createSync()

    SyncsDao.findAll(ids = Some(Seq(sync1.id, sync2.id))).map(_.id).sorted must be(
      Seq(sync1.id, sync2.id).sorted
    )

    SyncsDao.findAll(ids = Some(Nil)) must be(Nil)
    SyncsDao.findAll(ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    SyncsDao.findAll(ids = Some(Seq(sync1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(sync1.id))
  }

  "findAll by objectId and event" in {
    val start = createSync(createSyncForm(event = SyncEvent.Started))
    val completed = createSync(createSyncForm(event = SyncEvent.Completed))

    SyncsDao.findAll(
      ids = Some(Seq(start.id, completed.id)),
      event = Some(SyncEvent.Started)
    ).map(_.id) must be(Seq(start.id))

    SyncsDao.findAll(
      ids = Some(Seq(start.id, completed.id)),
      event = Some(SyncEvent.Completed)
    ).map(_.id) must be(Seq(completed.id))

    SyncsDao.findAll(
      ids = Some(Seq(start.id, completed.id)),
      event = Some(SyncEvent.UNDEFINED("other"))
    ) must be(Nil)
  }

  "findAll by objectId" in {
    val form = createSyncForm()
    val sync = createSync(form)

    SyncsDao.findAll(
      ids = Some(Seq(sync.id)),
      objectId = Some(form.objectId)
    ).map(_.id) must be(Seq(sync.id))

    SyncsDao.findAll(
      objectId = Some(UUID.randomUUID.toString)
    ) must be(Nil)
  }

  "purge executes" in {
    val sync = createSync()
    SyncsDao.purgeOld()
    SyncsDao.findById(sync.id).map(_.id) must be(Some(sync.id))
  }

}
