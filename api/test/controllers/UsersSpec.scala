package controllers

import java.util.UUID

import io.flow.common.v0.models.{Name, User}
import io.flow.dependency.v0.models.UserForm
import _root_.util.{DependencySpec, MockDependencyClient}

class UsersSpec extends DependencySpec with MockDependencyClient {

  import scala.concurrent.ExecutionContext.Implicits.global

  private[this] lazy val user1: User = createUser()
  private[this] lazy val user2: User = createUser()

  "GET /users requires a parameter" in {
    expectErrors {
      anonClient.users.get()
    }.genericError.messages must equal(
      Seq("Must specify id, email or identifier"),
    )
  }

  "GET /users allows anonymous access" in {
    await {
      anonClient.users.get(id = Some(createTestId()))
    } must be(Nil)
  }

  "GET /users by id" in {
    await(
      identifiedClient().users.get(id = Some(user1.id)),
    ).map(_.id) must contain theSameElementsAs Seq(user1.id)

    await(
      identifiedClient().users.get(id = Some(UUID.randomUUID.toString)),
    ).map(_.id) must be(
      Nil,
    )
  }

  "GET /users by email" in {
    await(
      identifiedClient().users.get(email = user1.email),
    ).map(_.email) must contain theSameElementsAs Seq(user1.email)

    await(
      identifiedClient().users.get(email = Some(UUID.randomUUID.toString)),
    ) must be(
      Nil,
    )
  }

  "GET /users/:id" in {
    await(identifiedClient().users.getById(user1.id)).id must be(user1.id)
    await(identifiedClient().users.getById(user2.id)).id must be(user2.id)

    expectNotFound {
      identifiedClient().users.getById(UUID.randomUUID.toString)
    }

    expectNotAuthorized {
      anonClient.users.getById(UUID.randomUUID.toString)
    }
  }

  "POST /users w/out name" in {
    val email = createTestEmail()
    val user = await(anonClient.users.post(UserForm(email = Some(email))))
    user.email must be(Some(email))
    user.name.first must be(None)
    user.name.last must be(None)
  }

  "POST /users w/ name" in {
    val email = createTestEmail()
    val user = await(
      anonClient.users.post(
        UserForm(
          email = Some(email),
          name = Some(
            Name(first = Some("Michael"), last = Some("Bryzek")),
          ),
        ),
      ),
    )
    user.email must be(Some(email))
    user.name.first must be(Some("Michael"))
    user.name.last must be(Some("Bryzek"))
  }

  "POST /users validates duplicate email" in {
    expectErrors(
      anonClient.users.post(UserForm(email = Some(user1.email.get))),
    ).genericError.messages must contain theSameElementsAs Seq("Email is already registered")
  }

  "POST /users validates empty email" in {
    expectErrors(
      anonClient.users.post(UserForm(email = Some("   "))),
    ).genericError.messages must contain theSameElementsAs Seq("Email address cannot be empty")
  }

  "POST /users validates email address format" in {
    expectErrors(
      anonClient.users.post(UserForm(email = Some("mbfoo.com"))),
    ).genericError.messages must contain theSameElementsAs Seq("Please enter a valid email address")
  }

}
