package db

import com.bryzek.dependency.v0.models.{UsernamePassword, Visibility}
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class ResolversDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] lazy val org = createOrganization()

  private[this] lazy val publicResolver = ResolversDao.findAll(
    Authorization.All,
    visibility = Some(Visibility.Public),
    uri = Some("http://jcenter.bintray.com/"),
    limit = 1
  ).headOption.getOrElse {
    sys.error("No public resolvers found")
  }

  "upsert" in {
    val form = createResolverForm(org)
    val resolver1 = ResolversDao.create(systemUser, form).right.get

    val resolver2 = ResolversDao.upsert(systemUser, form).right.get
    resolver1.id must be(resolver2.id)

    val resolver3 = createResolver(org)

    resolver2.id must not be(resolver3.id)
  }

  "findById" in {
    val resolver = createResolver(org)
    ResolversDao.findById(Authorization.All, resolver.id).map(_.id) must be(
      Some(resolver.id)
    )

    ResolversDao.findById(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "findByOrganizationIdAndUri" in {
    val resolver = createResolver(org)(createResolverForm(org))
    ResolversDao.findByOrganizationAndUri(Authorization.All, org.key, resolver.uri).map(_.id) must be(
      Some(resolver.id)
    )

    ResolversDao.findByOrganizationAndUri(Authorization.All, createTestKey(), resolver.uri).map(_.id) must be(None)
    ResolversDao.findByOrganizationAndUri(Authorization.All, org.key, UUID.randomUUID.toString).map(_.id) must be(None)
  }

  "findAll" must {

    "find by ids" in {
      val resolver1 = createResolver(org)
      val resolver2 = createResolver(org)

      ResolversDao.findAll(Authorization.All, ids = Some(Seq(resolver1.id, resolver2.id))).map(_.id).sorted must be(
        Seq(resolver1.id, resolver2.id).sorted
      )

      ResolversDao.findAll(Authorization.All, ids = Some(Nil)) must be(Nil)
      ResolversDao.findAll(Authorization.All, ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
      ResolversDao.findAll(Authorization.All, ids = Some(Seq(resolver1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(resolver1.id))
    }

    "find by organizationId" in {
      val resolver = createResolver(org)

      ResolversDao.findAll(Authorization.All, id = Some(resolver.id), organizationId = Some(org.id)).map(_.id).sorted must be(
        Seq(resolver.id)
      )

      ResolversDao.findAll(Authorization.All, id = Some(resolver.id), organizationId = Some(createOrganization().id)) must be(Nil)
    }

    "find by org" in {
      val resolver = createResolver(org)

      ResolversDao.findAll(Authorization.All, id = Some(resolver.id), organization = Some(org.key)).map(_.id).sorted must be(
        Seq(resolver.id)
      )

      ResolversDao.findAll(Authorization.All, id = Some(resolver.id), organization = Some(createOrganization().key)) must be(Nil)
    }

  }

  "organization" must {

    "be none for public resolvers" in {
      publicResolver.organization must be(None)
    }

    "be set for private resolvers" in {
      val resolver = createResolver(org)(createResolverForm(org = org, visibility = Visibility.Private))
      resolver.organization.map(_.id) must be(Some(org.id))
    }

  }

  "private resolvers sort after public" in {
    val resolver = createResolver(org)(createResolverForm(org = org, visibility = Visibility.Private))

    ResolversDao.findAll(
      Authorization.All,
      ids = Some(Seq(publicResolver.id, resolver.id))
    ).map(_.id) must be(Seq(publicResolver.id, resolver.id))
  }

  "private resolvers require authorization" in {
    val organization = createOrganization()
    val resolver = createResolver(org)(createResolverForm(
      org = organization,
      visibility = Visibility.Private
    ))

    ResolversDao.findAll(
      Authorization.All,
      id = Some(resolver.id)
    ).map(_.id) must be(Seq(resolver.id))

    ResolversDao.findAll(
      Authorization.Organization(organization.id),
      id = Some(resolver.id)
    ).map(_.id) must be(Seq(resolver.id))

    ResolversDao.findAll(
      Authorization.PublicOnly,
      id = Some(resolver.id)
    ) must be(Nil)
  }

  "with username only" in {
    val credentials = UsernamePassword(
      username = "foo"
    )
    val resolver = createResolver(org)(
      createResolverForm(org).copy(
        credentials = Some(
          credentials
        )
      )
    )
    //resolver.credentials must be(Some(UsernamePassword(credentials.username, None)))
    ResolversDao.credentials(resolver) must be(Some(credentials))
  }

  "with username and password" in {
    val credentials = UsernamePassword(
      username = "foo",
      password = Some("bar")
    )
    val resolver = createResolver(org)(
      createResolverForm(org).copy(
        credentials = Some(
          credentials
        )
      )
    )
    resolver.credentials must be(Some(UsernamePassword(
      username = credentials.username,
      password = Some("masked")
    )))

    ResolversDao.credentials(resolver) must be(Some(credentials))
  }

  "validates bad URL" in {
    ResolversDao.validate(
      systemUser,
      createResolverForm(org).copy(uri = "foo")
    ) must be(Seq("URI must start with http"))
  }

  "validates duplicate public resolver" in {
    ResolversDao.validate(
      systemUser,
      createResolverForm(org).copy(
        visibility = Visibility.Public,
        uri = publicResolver.uri
      )
    ) must be(Seq(s"Public resolver with uri[${publicResolver.uri}] already exists"))
  }

  "validates duplicate private resolver" in {
    val org = createOrganization()
    val resolver = createResolver(org)(
      createResolverForm(org = org)
    )
    ResolversDao.validate(
      systemUser,
      createResolverForm(org).copy(
        visibility = Visibility.Private,
        organization = org.key,
        uri = resolver.uri
      )
    ) must be(Seq(s"Organization already has a resolver with uri[${resolver.uri}]"))
  }

  "validates access to org" in {
    ResolversDao.validate(
      createUser(),
      createResolverForm(org).copy(
        visibility = Visibility.Private
      )
    ) must be(Seq(s"You do not have access to this organization"))
  }

}
