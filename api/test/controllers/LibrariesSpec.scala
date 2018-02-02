package controllers

import io.flow.dependency.v0.{Authorization, Client}
import io.flow.dependency.v0.models.ProjectForm
import io.flow.play.util.Validation

import java.util.UUID
import play.api.libs.ws._
import play.api.test._

class LibrariesSpec extends PlaySpecification with MockClient {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val org = createOrganization()
  lazy val library1 = createLibrary(org)()
  lazy val library2 = createLibrary(org)()

  "GET /libraries by id" in new WithServer(port=port) {
    await(
      client.libraries.get(id = Some(library1.id))
    ).map(_.id) must beEqualTo(
      Seq(library1.id)
    )

    await(
      client.libraries.get(id = Some(UUID.randomUUID.toString))
    ).map(_.id) must be(
      Nil
    )
  }

  "GET /libraries by groupId" in new WithServer(port=port) {
    await(
      client.libraries.get(groupId = Some(library1.groupId))
    ).map(_.groupId) must beEqualTo(
      Seq(library1.groupId)
    )

    await(
      client.libraries.get(groupId = Some(UUID.randomUUID.toString))
    ) must be(
      Nil
    )
  }

  "GET /libraries by artifactId" in new WithServer(port=port) {
    await(
      client.libraries.get(artifactId = Some(library1.artifactId))
    ).map(_.artifactId) must beEqualTo(
      Seq(library1.artifactId)
    )

    await(
      client.libraries.get(artifactId = Some(UUID.randomUUID.toString))
    ) must be(
      Nil
    )
  }

  "GET /libraries/:id" in new WithServer(port=port) {
    await(client.libraries.getById(library1.id)).id must beEqualTo(library1.id)
    await(client.libraries.getById(library2.id)).id must beEqualTo(library2.id)

    expectNotFound {
      client.libraries.getById(UUID.randomUUID.toString)
    }
  }

  "POST /libraries" in new WithServer(port=port) {
    val form = createLibraryForm(org)()
    val library = await(client.libraries.post(form))
    library.groupId must beEqualTo(form.groupId)
    library.artifactId must beEqualTo(form.artifactId)
  }

  "POST /libraries validates duplicate" in new WithServer(port=port) {
    expectErrors(
      client.libraries.post(
        createLibraryForm(org)().copy(
          groupId = library1.groupId,
          artifactId = library1.artifactId
        )
      )
    ).errors.map(_.message) must beEqualTo(
      Seq("Library with this group id and artifact id already exists")
    )
  }

  "DELETE /libraries" in new WithServer(port=port) {
    val library = createLibrary(org)()
    await(
      client.libraries.deleteById(library.id)
    ) must beEqualTo(())

    expectNotFound(
      client.libraries.getById(library.id)
    )

    expectNotFound(
      client.libraries.deleteById(library.id)
    )
  }

}
