package db

import org.scalatest._
import play.api.db._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class BinaryVersionsDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val org = createOrganization()

  "upsert" in {
    val binary = createBinary(org)
    val version1 = BinaryVersionsDao.upsert(systemUser, binary.id, "1.0")
    val version2 = BinaryVersionsDao.upsert(systemUser, binary.id, "1.0")
    val version3 = BinaryVersionsDao.upsert(systemUser, binary.id, "1.1")

    version1.id must be(version2.id)
    version2.id must not be(version3.id)
  }

  "findById" in {
    val version = createBinaryVersion(org)()
    BinaryVersionsDao.findById(Authorization.All, version.id).map(_.id) must be(
      Some(version.id)
    )

    BinaryVersionsDao.findById(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "findAll by ids" in {
    val version1 = createBinaryVersion(org)()
    val version2 = createBinaryVersion(org)()

    BinaryVersionsDao.findAll(Authorization.All, ids = Some(Seq(version1.id, version2.id))).map(_.id).sorted must be(
      Seq(version1.id, version2.id).sorted
    )

    BinaryVersionsDao.findAll(Authorization.All, ids = Some(Nil)) must be(Nil)
    BinaryVersionsDao.findAll(Authorization.All, ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    BinaryVersionsDao.findAll(Authorization.All, ids = Some(Seq(version1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(version1.id))
  }

  "delete" in {
    val binary = createBinary(org)
    val version1 = BinaryVersionsDao.upsert(systemUser, binary.id, "1.0")
    BinaryVersionsDao.delete(systemUser, version1)
    val version2 = BinaryVersionsDao.upsert(systemUser, binary.id, "1.0")
    val version3 = BinaryVersionsDao.upsert(systemUser, binary.id, "1.0")

    version1.id must not be(version2.id)
    version2.id must be(version3.id)
  }

}
