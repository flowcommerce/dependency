package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers._
import play.api.test._

class WebJarAssetsSpec extends PlaySpec with GuiceOneAppPerSuite {

  // Updates to webjars version can break serving of assets
  private def verifyCss(path: String) = {
    val request = FakeRequest(GET, path)
    val result = route(app, request).get

    status(result) mustBe OK
    contentType(result) mustBe Some("text/css")
  }

  "WebJar assets" should {
    "bootstrap.min.css" in {
      verifyCss("/assets/lib/bootstrap/css/bootstrap.min.css") // path as rendered via main.scala.html
    }

    "bootstrap-social.css" in {
      verifyCss("/assets/lib/bootstrap-social/bootstrap-social.css")
    }

    "font-awesome.css" in {
      verifyCss("/assets/lib/bootstrap-social/assets/css/font-awesome.css")
    }
  }
}
