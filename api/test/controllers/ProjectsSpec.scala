package controllers

import java.util.UUID

import play.api.test._
import util.{DependencySpec, MockDependencyClient}

class ProjectsSpec extends DependencySpec with MockDependencyClient {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val org = createOrganization()
  lazy val project1 = createProject(org)()
  lazy val project2 = createProject(org)()

  "GET /projects by id" in  {
    await(
      identifiedClient().projects.get(id = Some(project1.id))
    ).map(_.id) must contain theSameElementsAs Seq(project1.id)

    await(
      identifiedClient().projects.get(id = Some(UUID.randomUUID.toString))
    ).map(_.id) must be(
      Nil
    )
  }


}
