package db

import java.util.UUID

import util.DependencySpec

class BinaryVersionsDaoSpec extends DependencySpec {

  lazy val org = createOrganization()

  "upsert" in {
    val binary = createBinary(org)
    val version1 = binaryVersionsDao.upsert(systemUser, binary.id, "1.0")
    val version2 = binaryVersionsDao.upsert(systemUser, binary.id, "1.0")
    val version3 = binaryVersionsDao.upsert(systemUser, binary.id, "1.1")

    version1.id must be(version2.id)
    version2.id must not be(version3.id)
  }

  "findById" in {
    val version = createBinaryVersion(org)()
    binaryVersionsDao.findById(Authorization.All, version.id).map(_.id) must be(
      Some(version.id)
    )

    binaryVersionsDao.findById(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "findAll by ids" in {
    val version1 = createBinaryVersion(org)()
    val version2 = createBinaryVersion(org)()

    binaryVersionsDao.findAll(Authorization.All, ids = Some(Seq(version1.id, version2.id))).map(_.id).sorted must be(
      Seq(version1.id, version2.id).sorted
    )

    binaryVersionsDao.findAll(Authorization.All, ids = Some(Nil)) must be(Nil)
    binaryVersionsDao.findAll(Authorization.All, ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    binaryVersionsDao.findAll(Authorization.All, ids = Some(Seq(version1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(version1.id))
  }

  "delete" in {
    val binary = createBinary(org)
    val version1 = binaryVersionsDao.upsert(systemUser, binary.id, "1.0")
    binaryVersionsDao.delete(systemUser, version1)
    val version2 = binaryVersionsDao.upsert(systemUser, binary.id, "1.0")
    val version3 = binaryVersionsDao.upsert(systemUser, binary.id, "1.0")

    version1.id must not be(version2.id)
    version2.id must be(version3.id)
  }

}
