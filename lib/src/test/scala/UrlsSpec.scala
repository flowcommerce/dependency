package io.flow.dependency.lib

import io.flow.dependency.v0.models.{ItemSummaryUndefinedType, RecommendationType}
import io.flow.play.util.Config
import io.flow.test.utils.FlowPlaySpec

class UrlsSpec extends FlowPlaySpec with Factories {

  private[this] lazy val urls = Urls(
    new Config {
      override def optionalMap(name: String): Option[Map[String, Seq[String]]] = None
      override def optionalString(name: String): Option[String] = {
        if (name == "dependency.www.host")  {
          Some("http://localhost")
        } else {
          None
        }
      }

      override def optionalList(name: String): Option[Seq[String]] = {
        if (name == "dependency.www.host")  {
          Some(Seq("http://localhost"))
        } else {
          None
        }
      }

      override def get(name: String): Option[String] =
        if (name == "dependency.www.host")  {
          Some("http://localhost")
        } else {
          None
        }
    }
  )

  "www" in  {
    urls.www("/foo") must be("http://localhost/foo")
  }

  "recommendation" in  {
    val binary = makeRecommendation(`type` = RecommendationType.Binary)
    urls.recommendation(binary) must be(s"/binaries/${binary.`object`.id}")

    val library = makeRecommendation(`type` = RecommendationType.Library)
    urls.recommendation(library) must be(s"/libraries/${library.`object`.id}")

    val other = makeRecommendation(`type` = RecommendationType.UNDEFINED("other"))
    urls.recommendation(other) must be("#")
  }

  "itemSummary" in  {
    val binary = makeBinarySummary()
    urls.itemSummary(binary) must be(s"/binaries/${binary.id}")

    val library = makeLibrarySummary()
    urls.itemSummary(library) must be(s"/libraries/${library.id}")

    val project = makeProjectSummary()
    urls.itemSummary(project) must be(s"/projects/${project.id}")

    urls.itemSummary(ItemSummaryUndefinedType("other")) must be("#")
  }

}
