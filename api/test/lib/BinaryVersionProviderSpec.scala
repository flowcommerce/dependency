package io.flow.dependency.api.lib

import io.flow.dependency.v0.models.BinaryType

import play.api.libs.ws._
import play.api.test._

class BinaryVersionProviderSpec extends PlaySpecification {

  "scala" in {
    val versions = DefaultBinaryVersionProvider.versions(BinaryType.Scala).map(_.value)
    versions.contains("2.11.7") must beTrue
    versions.contains("2.9.1.final") must beTrue
    versions.contains("0.11.7") must beFalse
  }

  "sbt" in {
    val versions = DefaultBinaryVersionProvider.versions(BinaryType.Sbt).map(_.value)
    versions.contains("0.13.8") must beTrue
    versions.contains("0.13.9") must beTrue
    versions.contains("0.0.1") must beFalse
  }

  "undefined" in {
    DefaultBinaryVersionProvider.versions(BinaryType.UNDEFINED("other")) must be(Nil)
  }

  "toVersion" in {
    DefaultBinaryVersionProvider.toVersion("Scala 2.11.7").map(_.value) must beEqualTo(Some("2.11.7"))
    DefaultBinaryVersionProvider.toVersion("Scala 2.11.0-M4").map(_.value) must beEqualTo(Some("2.11.0-M4"))
    DefaultBinaryVersionProvider.toVersion("Scala 2.9.1.final").map(_.value) must beEqualTo(Some("2.9.1.final"))
    DefaultBinaryVersionProvider.toVersion("Scala License") must be(None)
  }

}
