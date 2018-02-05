package db

import java.util.UUID

import io.flow.dependency.v0.models.Role
import io.flow.common.v0.models.Name
import util.DependencySpec

class OrganizationsDaoSpec extends DependencySpec {

  "defaultUserName" in {
    val user = makeUser()

    organizationsDao.defaultUserName(
      user.copy(email = Some("mike@flow.io"))
    ) must be("mike")

    organizationsDao.defaultUserName(
      user.copy(email = Some("mbryzek@alum.mit.edu"))
    ) must be("mbryzek")

    organizationsDao.defaultUserName(
      user.copy(name = Name())
    ).length must be(organizationsDao.DefaultUserNameLength)

    organizationsDao.defaultUserName(
      user.copy(name = Name(first = Some("Michael")))
    ) must be("michael")

    organizationsDao.defaultUserName(
      user.copy(name = Name(last = Some("Bryzek")))
    ) must be("bryzek")

    organizationsDao.defaultUserName(
      user.copy(name = Name(first = Some("Michael"), last = Some("Bryzek")))
    ) must be("mbryzek")
  }

  "create" in {
    val form = createOrganizationForm()
    val organization = organizationsDao.create(systemUser, form).right.getOrElse {
      sys.error("Failed to create org")
    }
    organization.key must be(form.key)
  }

  "creation users added as admin of org" in {
    val user = createUser()
    val form = createOrganizationForm()
    val org = organizationsDao.create(user, form).right.getOrElse {
      sys.error("Failed to create org")
    }
    val membership = membershipsDao.findByOrganizationIdAndUserId(Authorization.All, org.id, user.id).getOrElse {
      sys.error("Failed to create membership record")
    }
    membership.role must be(Role.Admin)
  }

  "delete" in {
    val org = createOrganization()
    organizationsDao.delete(systemUser, org)
    organizationsDao.findById(Authorization.All, org.id) must be(None)
  }

  "findById" in {
    val organization = createOrganization()
    organizationsDao.findById(Authorization.All, organization.id).map(_.id) must be(
      Some(organization.id)
    )

    organizationsDao.findById(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "findAll by ids" in {
    val organization1 = createOrganization()
    val organization2 = createOrganization()

    organizationsDao.findAll(Authorization.All, ids = Some(Seq(organization1.id, organization2.id))).map(_.id).sorted must be(
      Seq(organization1.id, organization2.id).sorted
    )

    organizationsDao.findAll(Authorization.All, ids = Some(Nil)) must be(Nil)
    organizationsDao.findAll(Authorization.All, ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
    organizationsDao.findAll(Authorization.All, ids = Some(Seq(organization1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(organization1.id))
  }

  "findAll by userId includes user's org" in {
    val user = createUser()

    waitFor { () =>
      !organizationsDao.findAll(Authorization.All, forUserId = Some(user.id)).isEmpty
    }

    val org = organizationsDao.findAll(Authorization.All, forUserId = Some(user.id)).head
    organizationsDao.findAll(Authorization.All, id = Some(org.id), userId = Some(user.id)).map(_.id) must be(Seq(org.id))
    organizationsDao.findAll(Authorization.All, id = Some(org.id), userId = Some(UUID.randomUUID.toString)) must be(Nil)
  }

  "validate" must {

    "keep key url friendly" in {
      organizationsDao.validate(createOrganizationForm().copy(key = "flow commerce")) must be(
        Seq("Key must be in all lower case and contain alphanumerics only (-, _, and . are supported). A valid key would be: flow-commerce")
      )
    }

  }

  "authorization for organizations" in {
    val user = createUser()
    val org = createOrganization(user = user)

    organizationsDao.findAll(Authorization.PublicOnly, id = Some(org.id)) must be(Nil)
    organizationsDao.findAll(Authorization.All, id = Some(org.id)).map(_.id) must be(Seq(org.id))
    organizationsDao.findAll(Authorization.Organization(org.id), id = Some(org.id)).map(_.id) must be(Seq(org.id))
    organizationsDao.findAll(Authorization.Organization(createOrganization().id), id = Some(org.id)) must be(Nil)
    organizationsDao.findAll(Authorization.User(user.id), id = Some(org.id)).map(_.id) must be(Seq(org.id))
    organizationsDao.findAll(Authorization.User(createUser().id), id = Some(org.id)) must be(Nil)
  }

}
