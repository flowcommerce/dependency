package controllers

import java.util.UUID

import io.flow.common.v0.models.UserReference
import _root_.util.{DependencySpec, MockDependencyClient}

class ProjectsSpec extends DependencySpec with MockDependencyClient {

  import scala.concurrent.ExecutionContext.Implicits.global

  "GET /projects by id" in  {
    val org = createOrganization()
    val project1 = createProject(org)
    val client = identifiedClient(UserReference(systemUser.id))
    val x: Seq[io.flow.dependency.v0.models.Project] = await(client.projects.get(id = Option(project1.id)))
    x.map(_.id) must contain theSameElementsAs Seq(project1.id)

  }

  "GET /projects by id that does not exist" in  {
    await(
      identifiedClient().projects.get(id = Option(UUID.randomUUID.toString))
    ).map(_.id) must be(Nil)
  }

  "DELETE /projects" should {
    "work" in {
      val org = createOrganization(user = systemUser)
      val project = createProject(org)

      identifiedClient(systemUser).projects.deleteById(project.id)

      expectNotFound {
        identifiedClient().projects.getById(project.id)
      }
    }

    "validate membership" in {
      val org = createOrganization()
      val project = createProject(org)(createProjectForm(org).copy(visibility = Visibility.Public))

      val user2 = createUser()
      expectNotAuthorized(
        identifiedClient(UserReference(user2.id)).projects.deleteById(project.id)
      )

      await(
        identifiedClient().projects.getById(project.id)
      ).id must be (project.id)
    }

  }
}
