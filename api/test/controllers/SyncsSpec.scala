package controllers

import _root_.util.{DependencySpec, MockDependencyClient}
import org.scalatest.concurrent.ScalaFutures

class SyncsSpec extends DependencySpec with MockDependencyClient with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  val library = createLibrary()

  "POST /syncs" in {
    val futureResult = identifiedClient().syncs.postLibraries(Some("io.flow"))
    await(futureResult)
  }
}
