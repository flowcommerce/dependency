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
//    println("###" + x)
    x.map(_.id) must contain theSameElementsAs Seq(project1.id)

  }

  "GET /projects by id that does not exist" in  {
    await(
      identifiedClient().projects.get(id = Option(UUID.randomUUID.toString))
    ).map(_.id) must be(Nil)
  }

}
