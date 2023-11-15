package db

import java.util.UUID

import io.flow.dependency.v0.models.{Library, Organization, Visibility}
import util.DependencySpec

class LibrariesDaoSpec extends DependencySpec {

  private[this] lazy val org: Organization = createOrganization()

  private[this] def findAll(
    auth: Authorization = Authorization.All,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    organizationId: Option[String] = None,
    resolverId: Option[String] = None,
    prefix: Option[String] = None,
    limit: Option[Long] = None,
    offset: Long = 0,
  ): Seq[Library] = {
    librariesDao.findAll(
      auth,
      id = id,
      ids = ids,
      organizationId = organizationId,
      resolverId = resolverId,
      prefix = prefix,
      limit = limit,
      offset = offset,
    )
  }

  "findByGroupIdAndArtifactId" in {
    val library = createLibrary(org)
    librariesDao
      .findByGroupIdAndArtifactId(
        Authorization.All,
        library.groupId,
        library.artifactId,
      )
      .map(_.id) must be(Some(library.id))

    librariesDao.findByGroupIdAndArtifactId(
      Authorization.All,
      library.groupId + "-2",
      library.artifactId,
    ) must be(None)

    librariesDao.findByGroupIdAndArtifactId(
      Authorization.All,
      library.groupId,
      library.artifactId + "-2",
    ) must be(None)
  }

  "findById" in {
    val library = createLibrary(org)
    librariesDao.findById(Authorization.All, library.id).map(_.id) must be(
      Some(library.id),
    )

    librariesDao.findById(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "findAll by ids" in {
    val library1 = createLibrary(org)
    val library2 = createLibrary(org)

    findAll(ids = Some(Seq(library1.id, library2.id))).map(_.id) must be(
      Seq(library1, library2).sortWith { (x, y) => x.groupId.toString < y.groupId.toString }.map(_.id),
    )

    findAll(ids = Some(Nil)) must be(Nil)
    findAll(ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    findAll(ids = Some(Seq(library1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(library1.id))
  }

  "findAll by resolver" in {
    val resolver = createResolver(org)
    val form = createLibraryForm(org).copy(resolverId = resolver.id)
    val library = createLibrary(org)(form)

    findAll(resolverId = Some(resolver.id)).map(_.id) must be(Seq(library.id))
  }

  "findAll by prefix" in {
    val org = createOrganization()
    val resolver = createResolver(org)
    val library1 = createLibrary(org)(
      createLibraryForm(org).copy(resolverId = resolver.id, artifactId = "foo-bar"),
    )
    val library2 = createLibrary(org)(
      createLibraryForm(org).copy(resolverId = resolver.id, artifactId = "foo-baz"),
    )

    def ids(prefix: String) = {
      findAll(
        Authorization.All,
        organizationId = Some(org.id),
        prefix = Some(prefix),
      ).map(_.id).sorted
    }

    ids("foo") must equal(Seq(library1.id, library2.id).sorted)
    ids("foo-bar") must equal(Seq(library1.id))
    ids("foo-baz") must equal(Seq(library2.id))
    ids(createTestId()) must be(Nil)
  }

  "create" must {
    "validates empty group id" in {
      val form = createLibraryForm(org).copy(groupId = "   ")
      librariesDao.validate(form) must be(
        Seq("Group ID cannot be empty"),
      )
    }

    "validates empty artifact id" in {
      val form = createLibraryForm(org).copy(artifactId = "   ")
      librariesDao.validate(form) must be(
        Seq("Artifact ID cannot be empty"),
      )
    }

    "validates duplicates" in {
      val library = createLibrary(org)
      val form = createLibraryForm(org).copy(
        groupId = library.groupId,
        artifactId = library.artifactId,
      )
      librariesDao.validate(form) must be(
        Seq("Library with this group id and artifact id already exists"),
      )
    }
  }

  "authorization" must {

    "allow anybody to access a public library" in {
      val user = createUser()
      val org = createOrganization(user = user)
      val resolver = createResolver(org, user)(
        createResolverForm(org).copy(visibility = Visibility.Public),
      )
      val lib = createLibrary(org, user = user)(createLibraryForm(org)(resolver = resolver))

      findAll(Authorization.PublicOnly, id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
      findAll(id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
      findAll(Authorization.Organization(org.id), id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
      findAll(Authorization.Organization(createOrganization().id), id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
      findAll(Authorization.User(user.id), id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
    }

    "allow only users of an org to access a library w/ a private resolver" in {
      val user = createUser()
      val org = createOrganization(user = user)
      val resolver = createResolver(org, user)(
        createResolverForm(org).copy(visibility = Visibility.Private),
      )
      val lib = createLibrary(org, user = user)(createLibraryForm(org)(resolver = resolver))
      lib.resolver.visibility must be(Visibility.Private)

      findAll(Authorization.PublicOnly, id = Some(lib.id)) must be(Nil)
      findAll(id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
      findAll(Authorization.Organization(org.id), id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
      findAll(Authorization.Organization(createOrganization().id), id = Some(lib.id)) must be(Nil)
      findAll(Authorization.User(user.id), id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
      findAll(Authorization.User(createUser().id), id = Some(lib.id)) must be(Nil)
    }

  }

}
