package db

import io.flow.dependency.v0.models.{SyncEvent, Visibility}
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class LibrariesDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val org = createOrganization()

  "findByGroupIdAndArtifactId" in {
    val library = createLibrary(org)
    LibrariesDao.findByGroupIdAndArtifactId(
      Authorization.All,
      library.groupId,
      library.artifactId
    ).map(_.id) must be(Some(library.id))

    LibrariesDao.findByGroupIdAndArtifactId(
      Authorization.All,
      library.groupId + "-2",
      library.artifactId
    ) must be (None)

    LibrariesDao.findByGroupIdAndArtifactId(
      Authorization.All,
      library.groupId,
      library.artifactId + "-2"
    ) must be (None)
  }

  "findById" in {
    val library = createLibrary(org)
    LibrariesDao.findById(Authorization.All, library.id).map(_.id) must be(
      Some(library.id)
    )

    LibrariesDao.findById(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "findAll by ids" in {
    val library1 = createLibrary(org)
    val library2 = createLibrary(org)

    LibrariesDao.findAll(Authorization.All, ids = Some(Seq(library1.id, library2.id))).map(_.id) must be(
      Seq(library1, library2).sortWith { (x,y) => x.groupId.toString < y.groupId.toString }.map(_.id)
    )

    LibrariesDao.findAll(Authorization.All, ids = Some(Nil)) must be(Nil)
    LibrariesDao.findAll(Authorization.All, ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    LibrariesDao.findAll(Authorization.All, ids = Some(Seq(library1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(library1.id))
  }

  "findAll by resolver" in {
    val resolver = createResolver(org)
    val form = createLibraryForm(org).copy(resolverId = resolver.id)
    val library = createLibrary(org)(form)

    LibrariesDao.findAll(Authorization.All, resolverId = Some(resolver.id)).map(_.id) must be(Seq(library.id))
  }

  "create" must {
    "validates empty group id" in {
      val form = createLibraryForm(org).copy(groupId = "   ")
      LibrariesDao.validate(form) must be(
        Seq("Group ID cannot be empty")
      )
    }

    "validates empty artifact id" in {
      val form = createLibraryForm(org).copy(artifactId = "   ")
      LibrariesDao.validate(form) must be(
        Seq("Artifact ID cannot be empty")
      )
    }

    "validates duplicates" in {
      val library = createLibrary(org)
      val form = createLibraryForm(org).copy(
        groupId = library.groupId,
        artifactId = library.artifactId
      )
      LibrariesDao.validate(form) must be(
        Seq("Library with this group id and artifact id already exists")
      )
    }
  }

  "authorization" must {

    "allow anybody to access a public library" in {
      val user = createUser()
      val org = createOrganization(user = user)
      val resolver = createResolver(org, user) (
        createResolverForm(org).copy(visibility = Visibility.Public)
      )
      val lib = createLibrary(org, user = user)(createLibraryForm(org)(resolver = resolver))

      LibrariesDao.findAll(Authorization.PublicOnly, id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
      LibrariesDao.findAll(Authorization.All, id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
      LibrariesDao.findAll(Authorization.Organization(org.id), id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
      LibrariesDao.findAll(Authorization.Organization(createOrganization().id), id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
      LibrariesDao.findAll(Authorization.User(user.id), id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
    }

    "allow only users of an org to access a library w/ a private resolver" in {
      val user = createUser()
      val org = createOrganization(user = user)
      val resolver = createResolver(org, user) (
        createResolverForm(org).copy(visibility = Visibility.Private)
      )
      val lib = createLibrary(org, user = user)(createLibraryForm(org)(resolver = resolver))
      lib.resolver.visibility must be(Visibility.Private)

      LibrariesDao.findAll(Authorization.PublicOnly, id = Some(lib.id))must be(Nil)
      LibrariesDao.findAll(Authorization.All, id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
      LibrariesDao.findAll(Authorization.Organization(org.id), id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
      LibrariesDao.findAll(Authorization.Organization(createOrganization().id), id = Some(lib.id))must be(Nil)
      LibrariesDao.findAll(Authorization.User(user.id), id = Some(lib.id)).map(_.id) must be(Seq(lib.id))
      LibrariesDao.findAll(Authorization.User(createUser().id), id = Some(lib.id)) must be(Nil)
    }

  }

}
