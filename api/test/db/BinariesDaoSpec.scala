package db

import java.util.UUID

import io.flow.dependency.v0.models.{BinaryType, Organization, SyncEvent}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import util.DependencySpec

class BinariesDaoSpec extends DependencySpec with Eventually with IntegrationPatience {

  private[this] lazy val org: Organization = createOrganization()

  "findByName" in {
    val lang = createBinary(org)
    binariesDao.findByName(lang.name.toString).map(_.name) must be(
      Some(lang.name)
    )

    binariesDao.findByName(UUID.randomUUID.toString) must be(None)
  }

  "findById" in {
    val lang = createBinary(org)
    binariesDao.findById(lang.id).map(_.id) must be(
      Some(lang.id)
    )

    binariesDao.findById(UUID.randomUUID.toString) must be(None)
  }

  "findAll by ids" in {
    val binary1 = createBinary(org)
    val binary2 = createBinary(org)

    binariesDao.findAll(ids = Some(Seq(binary1.id, binary2.id))).map(_.id).sorted must be(
      Seq(binary1, binary2).map(_.id).sorted
    )

    binariesDao.findAll(ids = Some(Nil)) must be(Nil)
    binariesDao.findAll(ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    binariesDao.findAll(ids = Some(Seq(binary1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(binary1.id))
  }

  "findAll by isSynced" in {
    val binary = createBinary(org)
    createSync(createSyncForm(objectId = binary.id, event = SyncEvent.Completed))

    binariesDao.findAll(id = Some(binary.id), isSynced = Some(true)).map(_.id) must be(Seq(binary.id))
    binariesDao.findAll(id = Some(binary.id), isSynced = Some(false)) must be(Nil)
  }

  "findAll by projectId" in {
    val (project, binaryVersion) = createProjectWithBinary(org)

    eventually {
      binariesDao.findAll(id = Some(binaryVersion.binary.id), projectId = Some(project.id)).map(_.id) must be(
        Seq(binaryVersion.binary.id)
      )
      binariesDao.findAll(id = Some(binaryVersion.binary.id), projectId = Some(createProject().id)) must be(Nil)
    }
  }

  "create" must {
    "validates empty name" in {
      val form = createBinaryForm(org).copy(name = BinaryType.UNDEFINED("   "))
      binariesDao.validate(form) must be(
        Seq("Name cannot be empty")
      )
    }

    "validates duplicate names" in {
      val lang = createBinary(org)
      val form = createBinaryForm(org).copy(name = BinaryType.UNDEFINED(lang.name.toString.toUpperCase))
      binariesDao.validate(form) must be(
        Seq("Binary with this name already exists")
      )
    }
  }

}
