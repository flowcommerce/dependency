package controllers

import io.flow.dependency.v0.{Authorization, Client}
import io.flow.dependency.v0.models.BinaryForm
import io.flow.play.util.Validation

import java.util.UUID
import play.api.libs.ws._
import play.api.test._

class BinariesSpec extends PlaySpecification with MockClient {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val org = createOrganization()
  lazy val binary1 = createBinary(org)()
  lazy val binary2 = createBinary(org)()

  "GET /binaries by id" in new WithServer(port=port) {
    await(
      client.binaries.get(id = Some(binary1.id))
    ).map(_.id) must beEqualTo(
      Seq(binary1.id)
    )

    await(
      client.binaries.get(id = Some(UUID.randomUUID.toString))
    ).map(_.id) must be(
      Nil
    )
  }

  "GET /binaries by name" in new WithServer(port=port) {
    await(
      client.binaries.get(name = Some(binary1.name.toString))
    ).map(_.name) must beEqualTo(
      Seq(binary1.name)
    )

    await(
      client.binaries.get(name = Some(binary1.name.toString.toUpperCase))
    ).map(_.name) must beEqualTo(
      Seq(binary1.name)
    )

    await(
      client.binaries.get(name = Some(UUID.randomUUID.toString))
    ) must be(
      Nil
    )
  }

  "GET /binaries/:id" in new WithServer(port=port) {
    await(client.binaries.getById(binary1.id)).id must beEqualTo(binary1.id)
    await(client.binaries.getById(binary2.id)).id must beEqualTo(binary2.id)

    expectNotFound {
      client.binaries.getById(UUID.randomUUID.toString)
    }
  }

  "POST /binaries" in new WithServer(port=port) {
    val form = createBinaryForm(org)
    val binary = await(client.binaries.post(form))
    binary.name must beEqualTo(form.name)
  }

  "POST /binaries validates duplicate name" in new WithServer(port=port) {
    expectErrors(
      client.binaries.post(createBinaryForm(org).copy(name = binary1.name))
    ).errors.map(_.message) must beEqualTo(
      Seq("Binary with this name already exists")
    )
  }

  "DELETE /binaries" in new WithServer(port=port) {
    val binary = createBinary(org)()
    await(
      client.binaries.deleteById(binary.id)
    ) must beEqualTo(())

    expectNotFound(
      client.binaries.getById(binary.id)
    )

    expectNotFound(
      client.binaries.deleteById(binary.id)
    )
  }

}
