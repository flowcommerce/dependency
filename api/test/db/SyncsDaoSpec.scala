package db

import java.util.UUID

import io.flow.dependency.v0.models.SyncEvent
import util.DependencySpec

class SyncsDaoSpec extends DependencySpec {

  private[this] lazy val org = createOrganization()

  "create" in {
    val form = createSyncForm()
    val sync = syncsDao.create(form)

    sync.event must be(form.event)
  }

  "withStartedAndCompleted" in {
    val project = createProject(org)
    syncsDao.withStartedAndCompleted("project", project.id) {
      // NO-OP
    }
    val events = syncsDao.findAll(objectId = Some(project.id)).map(_.event)
    events.contains(SyncEvent.Started) must be(true)
    events.contains(SyncEvent.Completed) must be(true)
  }

  "recordStarted" in {
    val project = createProject(org)
    syncsDao.recordStarted("project", project.id)
    syncsDao.findAll(objectId = Some(project.id)).map(_.event).contains(SyncEvent.Started) must be(true)
  }

  "recordCompleted" in {
    val project = createProject(org)
    syncsDao.recordCompleted("project", project.id)
    syncsDao.findAll(objectId = Some(project.id)).map(_.event).contains(SyncEvent.Completed) must be(true)
  }

  "findById" in {
    val sync = createSync()
    syncsDao.findById(sync.id).map(_.id) must be(
      Some(sync.id)
    )

    syncsDao.findById(UUID.randomUUID.toString) must be(None)
  }

  "findAll by ids" in {
    val sync1 = createSync()
    val sync2 = createSync()

    syncsDao.findAll(ids = Some(Seq(sync1.id, sync2.id))).map(_.id).sorted must be(
      Seq(sync1.id, sync2.id).sorted
    )

    syncsDao.findAll(ids = Some(Nil)) must be(Nil)
    syncsDao.findAll(ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    syncsDao.findAll(ids = Some(Seq(sync1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(sync1.id))
  }

  "findAll by objectId and event" in {
    val start = createSync(createSyncForm(event = SyncEvent.Started))
    val completed = createSync(createSyncForm(event = SyncEvent.Completed))

    syncsDao.findAll(
      ids = Some(Seq(start.id, completed.id)),
      event = Some(SyncEvent.Started)
    ).map(_.id) must be(Seq(start.id))

    syncsDao.findAll(
      ids = Some(Seq(start.id, completed.id)),
      event = Some(SyncEvent.Completed)
    ).map(_.id) must be(Seq(completed.id))

    syncsDao.findAll(
      ids = Some(Seq(start.id, completed.id)),
      event = Some(SyncEvent.UNDEFINED("other"))
    ) must be(Nil)
  }

  "findAll by objectId" in {
    val form = createSyncForm()
    val sync = createSync(form)

    syncsDao.findAll(
      ids = Some(Seq(sync.id)),
      objectId = Some(form.objectId)
    ).map(_.id) must be(Seq(sync.id))

    syncsDao.findAll(
      objectId = Some(UUID.randomUUID.toString)
    ) must be(Nil)
  }

  "purge executes" in {
    val sync = createSync()
    syncsDao.purgeOld()
    syncsDao.findById(sync.id).map(_.id) must be(Some(sync.id))
  }

}
