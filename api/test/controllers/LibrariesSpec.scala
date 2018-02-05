package controllers

import java.util.UUID

import play.api.test._
import util.{DependencySpec, MockDependencyClient}

class LibrariesSpec extends DependencySpec with MockDependencyClient {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val org = createOrganization()
  lazy val library1 = createLibrary(org)()
  lazy val library2 = createLibrary(org)()

  "GET /libraries by id" in  {
    await(
      identifiedClient().libraries.get(id = Some(library1.id))
    ).map(_.id) must contain theSameElementsAs (
      Seq(library1.id)
      )

    await(
      identifiedClient().libraries.get(id = Some(UUID.randomUUID.toString))
    ).map(_.id) must be(
      Nil
    )
  }

  "GET /libraries by groupId" in  {
    await(
      identifiedClient().libraries.get(groupId = Some(library1.groupId))
    ).map(_.groupId) must contain theSameElementsAs (
      Seq(library1.groupId)
      )

    await(
      identifiedClient().libraries.get(groupId = Some(UUID.randomUUID.toString))
    ) must be(
      Nil
    )
  }

  "GET /libraries by artifactId" in  {
    await(
      identifiedClient().libraries.get(artifactId = Some(library1.artifactId))
    ).map(_.artifactId) must contain theSameElementsAs Seq(library1.artifactId)

    await(
      identifiedClient().libraries.get(artifactId = Some(UUID.randomUUID.toString))
    ) must be(
      Nil
    )
  }

  "GET /libraries/:id" in  {
    await(identifiedClient().libraries.getById(library1.id)).id must be(library1.id)
    await(identifiedClient().libraries.getById(library2.id)).id must be(library2.id)

    expectNotFound {
      identifiedClient().libraries.getById(UUID.randomUUID.toString)
    }
  }

  "POST /libraries" in  {
    val form = createLibraryForm(org)()
    val library = await(identifiedClient().libraries.post(form))
    library.groupId must be(form.groupId)
    library.artifactId must be(form.artifactId)
  }

  "POST /libraries validates duplicate" in  {
    expectErrors(
      identifiedClient().libraries.post(
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
      identifiedClient().libraries.deleteById(library.id)
    ) must be(())

    expectNotFound(
      identifiedClient().libraries.getById(library.id)
    )

    expectNotFound(
      identifiedClient().libraries.deleteById(library.id)
    )
  }

}
