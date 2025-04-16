package controllers

import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.webjars.play.WebJarsUtil
import play.api.test.Helpers._
import play.api.test._

class WebJarAssetsSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterAll {

  private lazy val webJarsUtil = inject[WebJarsUtil]

  // the first attempt to get a located resource always gives 404, so
  // we compensate by running one before the tests.
  override def beforeAll(): Unit = {
    val url = webJarsUtil.locate("index.html").url.get
    val result = route(app, FakeRequest(GET, url)).get
    locally(status(result))
    ()
  }

  // Updates to webjars version can break serving of assets
  private def verifyCss(file: String) = {
    locally(webJarsUtil.locate(file).url.get)
    val url = webJarsUtil.locate(file).url.get

    val request = FakeRequest(GET, url)
    val result = route(app, request).get

    status(result) mustBe OK
    contentType(result) mustBe Some("text/css")
  }

  "WebJar assets" should {
    "bootstrap.min.css" in {
      verifyCss("bootstrap.min.css")
    }

    "bootstrap-social.css" in {
      verifyCss("bootstrap-social.css")
    }

    "font-awesome.css" in {
      verifyCss("font-awesome.css")
    }
  }
}
