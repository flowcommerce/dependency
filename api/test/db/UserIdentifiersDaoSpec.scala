package db

import java.util.UUID

import io.flow.dependency.v0.models.UserIdentifier
import io.flow.common.v0.models.User
import util.DependencySpec

class UserIdentifiersDaoSpec extends DependencySpec {

  def createUserIdentifier(): (User, UserIdentifier) = {
    val user = createUser()
    val userIdentifier = userIdentifiersDao.createForUser(systemUser, user)
    (user, userIdentifier)
  }

  "createForUser" in {
    val user = createUser()
    val identifier1 = userIdentifiersDao.createForUser(systemUser, user)
    val identifier2 = userIdentifiersDao.createForUser(systemUser, user)

    identifier1.value must not be (identifier2.value)
    identifier1.user.id must be(user.id)
    identifier2.user.id must be(user.id)
    identifier1.value.length must be(60)
  }

  "findById" in {
    val (_, identifier) = createUserIdentifier()

    userIdentifiersDao.findById(identifier.id).map(_.id) must be(
      Some(identifier.id)
    )

    userIdentifiersDao.findById(UUID.randomUUID.toString) must be(None)
  }

  "findAll" must {
    "filter by ids" in {
      val (_, identifier1) = createUserIdentifier()
      val (_, identifier2) = createUserIdentifier()

      userIdentifiersDao.findAll(ids = Some(Seq(identifier1.id, identifier2.id))).map(_.id).sorted must be(
        Seq(identifier1.id, identifier2.id).sorted
      )

      userIdentifiersDao.findAll(ids = Some(Nil)) must be(Nil)
      userIdentifiersDao.findAll(ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
      userIdentifiersDao.findAll(ids = Some(Seq(identifier1.id, UUID.randomUUID.toString))).map(_.id) must be(
        Seq(identifier1.id)
      )
    }

    "filter by identifier" in {
      val (_, identifier) = createUserIdentifier()

      userIdentifiersDao.findAll(value = Some(identifier.value)).map(_.id) must be(Seq(identifier.id))
      userIdentifiersDao.findAll(value = Some(createTestKey())) must be(Nil)
    }
  }
}
