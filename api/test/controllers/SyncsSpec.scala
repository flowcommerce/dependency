package controllers

import _root_.util.{DependencySpec, MockDependencyClient}
import org.scalatest.concurrent.ScalaFutures

class SyncsSpec extends DependencySpec with MockDependencyClient with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  "POST /syncs" in {
    await(
      identifiedClient().syncs.postLibrariesByOrganization(createTestId()),
    )
  }
}
