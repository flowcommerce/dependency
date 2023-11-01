package db

import java.util.UUID

import io.flow.dependency.v0.models.{VersionForm, Visibility}
import util.DependencySpec

class LibraryVersionsDaoSpec extends DependencySpec {

  lazy val org = createOrganization()

  "upsert" in {
    val library = createLibrary(org)()
    val version1 = libraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", None))
    val version2 = libraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", None))
    val version3 = libraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.1", None))

    version1.id must be(version2.id)
    version2.id must not be (version3.id)
  }

  "upsert with crossBuildVersion" in {
    val library = createLibrary(org)()
    val version0 = libraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", None))
    val version1 = libraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", Some("2.11")))
    val version2 = libraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", Some("2.11")))
    val version3 = libraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.1", Some("2.10")))
    val version4 = libraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.1", Some("2.9.3")))

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

    version0.id must not be (version1.id)
    version1.id must be(version2.id)
    version2.id must not be (version3.id)
    version3.id must not be (version4.id)
  }

  "findById" in {
    val version = createLibraryVersion(org)
    libraryVersionsDao.findById(Authorization.All, version.id).map(_.id) must be(
      Some(version.id)
    )

    libraryVersionsDao.findById(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "findAll by ids" in {
    val version1 = createLibraryVersion(org)
    val version2 = createLibraryVersion(org)

    libraryVersionsDao
      .findAll(Authorization.All, limit = None, ids = Some(Seq(version1.id, version2.id)))
      .map(_.id)
      .sorted must be(
      Seq(version1.id, version2.id).sorted
    )

    libraryVersionsDao.findAll(Authorization.All, limit = None, ids = Some(Nil)) must be(Nil)
    libraryVersionsDao.findAll(Authorization.All, limit = None, ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    libraryVersionsDao
      .findAll(Authorization.All, limit = None, ids = Some(Seq(version1.id, UUID.randomUUID.toString)))
      .map(_.id) must be(Seq(version1.id))
  }

  "delete" in {
    val library = createLibrary(org)()
    val version1 = libraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", None))
    libraryVersionsDao.delete(systemUser, version1)
    val version2 = libraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", None))
    val version3 = libraryVersionsDao.upsert(systemUser, library.id, VersionForm("1.0", None))

    version1.id must not be (version2.id)
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

      libraryVersionsDao
        .findAll(Authorization.PublicOnly, limit = None, id = Some(libraryVersion.id))
        .map(_.id) must be(Seq(libraryVersion.id))
      libraryVersionsDao.findAll(Authorization.All, limit = None, id = Some(libraryVersion.id)).map(_.id) must be(
        Seq(libraryVersion.id)
      )
      libraryVersionsDao
        .findAll(Authorization.Organization(org.id), limit = None, id = Some(libraryVersion.id))
        .map(_.id) must be(Seq(libraryVersion.id))
      libraryVersionsDao
        .findAll(Authorization.Organization(createOrganization().id), limit = None, id = Some(libraryVersion.id))
        .map(_.id) must be(Seq(libraryVersion.id))
      libraryVersionsDao
        .findAll(Authorization.User(user.id), limit = None, id = Some(libraryVersion.id))
        .map(_.id) must be(Seq(libraryVersion.id))
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

      libraryVersionsDao.findAll(Authorization.All, limit = None, id = Some(libraryVersion.id)).map(_.id) must be(
        Seq(libraryVersion.id)
      )
      libraryVersionsDao
        .findAll(Authorization.Organization(org.id), limit = None, id = Some(libraryVersion.id))
        .map(_.id) must be(Seq(libraryVersion.id))
      libraryVersionsDao
        .findAll(Authorization.User(user.id), limit = None, id = Some(libraryVersion.id))
        .map(_.id) must be(Seq(libraryVersion.id))

      libraryVersionsDao.findAll(Authorization.PublicOnly, limit = None, id = Some(libraryVersion.id)) must be(Nil)
      libraryVersionsDao.findAll(
        Authorization.Organization(createOrganization().id),
        limit = None,
        id = Some(libraryVersion.id)
      ) must be(Nil)
      libraryVersionsDao.findAll(
        Authorization.User(createUser().id),
        limit = None,
        id = Some(libraryVersion.id)
      ) must be(Nil)
    }

  }

}
