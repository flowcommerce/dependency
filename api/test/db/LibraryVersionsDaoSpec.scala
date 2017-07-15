package db

import com.bryzek.dependency.v0.models.{VersionForm, Visibility}
import org.scalatest._
import play.api.db._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class LibraryVersionsDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val org = createOrganization()

  "upsert" in {
    val library = createLibrary(org)()
    val version1 = LibraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", None))
    val version2 = LibraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", None))
    val version3 = LibraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.1", None))

    version1.id must be(version2.id)
    version2.id must not be(version3.id)
  }

  "upsert with crossBuildVersion" in {
    val library = createLibrary(org)()
    val version0 = LibraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", None))
    val version1 = LibraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", Some("2.11")))
    val version2 = LibraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", Some("2.11")))
    val version3 = LibraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.1", Some("2.10")))
    val version4 = LibraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.1", Some("2.9.3")))

    version0.version must be("1.0")
    version0.crossBuildVersion must be(None)

    version1.version must be("1.0")
    version1.crossBuildVersion must be(Some("2.11"))

    version2.version must be("1.0")
    version2.crossBuildVersion must be(Some("2.11"))

    version3.version must be("1.1")
    version3.crossBuildVersion must be(Some("2.10"))

    version4.version must be("1.1")
    version4.crossBuildVersion must be(Some("2.9.3"))

    version0.id must not be(version1.id)
    version1.id must be(version2.id)
    version2.id must not be(version3.id)
    version3.id must not be(version4.id)
  }

  "findById" in {
    val version = createLibraryVersion(org)
    LibraryVersionsDao.findById(Authorization.All, version.id).map(_.id) must be(
      Some(version.id)
    )

    LibraryVersionsDao.findById(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "findAll by ids" in {
    val version1 = createLibraryVersion(org)
    val version2 = createLibraryVersion(org)

    LibraryVersionsDao.findAll(Authorization.All, limit = None, ids = Some(Seq(version1.id, version2.id))).map(_.id).sorted must be(
      Seq(version1.id, version2.id).sorted
    )

    LibraryVersionsDao.findAll(Authorization.All, limit = None, ids = Some(Nil)) must be(Nil)
    LibraryVersionsDao.findAll(Authorization.All, limit = None, ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    LibraryVersionsDao.findAll(Authorization.All, limit = None, ids = Some(Seq(version1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(version1.id))
  }

  "delete" in {
    val library = createLibrary(org)()
    val version1 = LibraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", None))
    LibraryVersionsDao.delete(systemUser, version1)
    val version2 = LibraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", None))
    val version3 = LibraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", None))

    version1.id must not be(version2.id)
    version2.id must be(version3.id)
  }

  "authorization" must {

    "allow all to access public libraries" in {
      val user = createUser()
      val org = createOrganization(user = user)
      val resolver = createResolver(org, user)(
        createResolverForm(org = org, visibility = Visibility.Public)
      )
      val library = createLibrary(org, user)(
        createLibraryForm(org, user)(resolver = resolver)
      )
      val libraryVersion = createLibraryVersion(org, user = user)(library = library)
      libraryVersion.library.resolver.visibility must be(Visibility.Public)

      LibraryVersionsDao.findAll(Authorization.PublicOnly, limit = None, id = Some(libraryVersion.id)).map(_.id) must be(Seq(libraryVersion.id))
      LibraryVersionsDao.findAll(Authorization.All, limit = None, id = Some(libraryVersion.id)).map(_.id) must be(Seq(libraryVersion.id))
      LibraryVersionsDao.findAll(Authorization.Organization(org.id), limit = None, id = Some(libraryVersion.id)).map(_.id) must be(Seq(libraryVersion.id))
      LibraryVersionsDao.findAll(Authorization.Organization(createOrganization().id), limit = None, id = Some(libraryVersion.id)).map(_.id) must be(Seq(libraryVersion.id))
      LibraryVersionsDao.findAll(Authorization.User(user.id), limit = None, id = Some(libraryVersion.id)).map(_.id) must be(Seq(libraryVersion.id))
    }

    "allow only org users to access private libraries" in {
      val user = createUser()
      val org = createOrganization(user = user)
      val resolver = createResolver(org, user)(
        createResolverForm(org = org, visibility = Visibility.Private)
      )
      val library = createLibrary(org, user)(
        createLibraryForm(org, user)(resolver = resolver)
      )
      val libraryVersion = createLibraryVersion(org, user = user)(library = library)
      libraryVersion.library.resolver.visibility must be(Visibility.Private)

      LibraryVersionsDao.findAll(Authorization.All, limit = None, id = Some(libraryVersion.id)).map(_.id) must be(Seq(libraryVersion.id))
      LibraryVersionsDao.findAll(Authorization.Organization(org.id), limit = None, id = Some(libraryVersion.id)).map(_.id) must be(Seq(libraryVersion.id))
      LibraryVersionsDao.findAll(Authorization.User(user.id), limit = None, id = Some(libraryVersion.id)).map(_.id) must be(Seq(libraryVersion.id))

      LibraryVersionsDao.findAll(Authorization.PublicOnly, limit = None, id = Some(libraryVersion.id)) must be(Nil)
      LibraryVersionsDao.findAll(Authorization.Organization(createOrganization().id), limit = None, id = Some(libraryVersion.id)) must be(Nil)
      LibraryVersionsDao.findAll(Authorization.User(createUser().id), limit = None, id = Some(libraryVersion.id)) must be(Nil)
    }

 }

}
