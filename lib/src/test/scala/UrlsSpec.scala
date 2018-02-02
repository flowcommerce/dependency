package io.flow.dependency.lib

import io.flow.play.util.Config
import io.flow.dependency.v0.models.{ItemSummaryUndefinedType, RecommendationType}
import org.scalatest.{FunSpec, Matchers}

class UrlsSpec extends FunSpec with Matchers with Factories {

  private[this] lazy val urls = Urls(
    new Config {
      def optionalString(name: String): Option[String] = {
        if (name == "dependency.www.host") {
          Some("http://localhost")
        } else {
          None
        }
      }
    }
  )

  it("www") {
    urls.www("/foo") should be("http://localhost/foo")
  }

  it("recommendation") {
    val binary = makeRecommendation(`type` = RecommendationType.Binary)
    urls.recommendation(binary) should be(s"/binaries/${binary.`object`.id}")

    val library = makeRecommendation(`type` = RecommendationType.Library)
    urls.recommendation(library) should be(s"/libraries/${library.`object`.id}")

    val other = makeRecommendation(`type` = RecommendationType.UNDEFINED("other"))
    urls.recommendation(other) should be("#")
  }

  it("itemSummary") {
    val binary = makeBinarySummary()
    urls.itemSummary(binary) should be(s"/binaries/${binary.id}")

    val library = makeLibrarySummary()
    urls.itemSummary(library) should be(s"/libraries/${library.id}")

    val project = makeProjectSummary()
    urls.itemSummary(project) should be(s"/projects/${project.id}")

    urls.itemSummary(ItemSummaryUndefinedType("other")) should be("#")
  }

}
