package controllers

import java.util.UUID

import io.flow.common.v0.models.UserReference
import _root_.util.{DependencySpec, MockDependencyClient}

class LibrariesSpec extends DependencySpec with MockDependencyClient {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val org = createOrganization()
  lazy val library1 = createLibrary(org)()
  lazy val library2 = createLibrary(org)()
  lazy val client = identifiedClient(UserReference(systemUser.id))

  "GET /libraries by id" in  {
    await(
      client.libraries.get(id = Some(library1.id))
    ).map(_.id) must contain theSameElementsAs (
      Seq(library1.id)
      )

    await(
      client.libraries.get(id = Some(UUID.randomUUID.toString))
    ).map(_.id) must be(
      Nil
    )
  }

  "GET /libraries by groupId" in  {
    await(
      client.libraries.get(groupId = Some(library1.groupId))
    ).map(_.groupId) must contain theSameElementsAs (
      Seq(library1.groupId)
      )

    await(
      client.libraries.get(groupId = Some(UUID.randomUUID.toString))
    ) must be(
      Nil
    )
  }

  "GET /libraries by artifactId" in  {
    await(
      client.libraries.get(artifactId = Some(library1.artifactId))
    ).map(_.artifactId) must contain theSameElementsAs Seq(library1.artifactId)

    await(
      client.libraries.get(artifactId = Some(UUID.randomUUID.toString))
    ) must be(
      Nil
    )
  }

  "GET /libraries/:id" in  {
    await(client.libraries.getById(library1.id)).id must be(library1.id)
    await(client.libraries.getById(library2.id)).id must be(library2.id)

    expectNotFound {
      client.libraries.getById(UUID.randomUUID.toString)
    }
  }

  "POST /libraries" in  {
    val form = createLibraryForm(org)()
    val library = await(client.libraries.post(form))
    library.groupId must be(form.groupId)
    library.artifactId must be(form.artifactId)
  }

  "POST /libraries validates duplicate" in  {
    expectErrors(
      client.libraries.post(
        createLibraryForm(org)().copy(
          groupId = library1.groupId,
          artifactId = library1.artifactId
        )
      )
    ).genericErrors.flatMap(_.messages) must contain theSameElementsAs Seq("Library with this group id and artifact id already exists")
  }

  "DELETE /libraries" in  {
    val library = createLibrary(org)()
    await(
      client.libraries.deleteById(library.id)
    ) must be(())

    expectNotFound(
      client.libraries.getById(library.id)
    )

    expectNotFound(
      client.libraries.deleteById(library.id)
    )
  }

}
