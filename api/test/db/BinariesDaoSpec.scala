package db

import com.bryzek.dependency.v0.models.{BinaryType, SyncEvent}
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class BinariesDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val org = createOrganization()

  "findByName" in {
    val lang = createBinary(org)
    BinariesDao.findByName(Authorization.All, lang.name.toString).map(_.name) must be(
      Some(lang.name)
    )

    BinariesDao.findByName(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "findById" in {
    val lang = createBinary(org)
    BinariesDao.findById(Authorization.All, lang.id).map(_.id) must be(
      Some(lang.id)
    )

    BinariesDao.findById(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "findAll by ids" in {
    val binary1 = createBinary(org)
    val binary2 = createBinary(org)

    BinariesDao.findAll(Authorization.All, ids = Some(Seq(binary1.id, binary2.id))).map(_.id).sorted must be(
      Seq(binary1, binary2).map(_.id).sorted
    )

    BinariesDao.findAll(Authorization.All, ids = Some(Nil)) must be(Nil)
    BinariesDao.findAll(Authorization.All, ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    BinariesDao.findAll(Authorization.All, ids = Some(Seq(binary1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(binary1.id))
  }

  "findAll by isSynced" in {
    val binary = createBinary(org)
    createSync(createSyncForm(objectId = binary.id, event = SyncEvent.Completed))

    BinariesDao.findAll(Authorization.All, id = Some(binary.id), isSynced = Some(true)).map(_.id) must be(Seq(binary.id))
    BinariesDao.findAll(Authorization.All, id = Some(binary.id), isSynced = Some(false)) must be(Nil)
  }

  "findAll by projectId" in {
    val (project, binaryVersion) = createProjectWithBinary(org)

    BinariesDao.findAll(Authorization.All, id = Some(binaryVersion.binary.id), projectId = Some(project.id)).map(_.id) must be(Seq(binaryVersion.binary.id))
    BinariesDao.findAll(Authorization.All, id = Some(binaryVersion.binary.id), projectId = Some(createProject().id)) must be(Nil)
  }

  "create" must {
    "validates empty name" in {
      val form = createBinaryForm(org).copy(name = BinaryType.UNDEFINED("   "))
      BinariesDao.validate(form) must be(
        Seq("Name cannot be empty")
      )
    }

    "validates duplicate names" in {
      val lang = createBinary(org)
      val form = createBinaryForm(org).copy(name = BinaryType.UNDEFINED(lang.name.toString.toUpperCase))
      BinariesDao.validate(form) must be(
        Seq("Binary with this name already exists")
      )
    }
  }

}
