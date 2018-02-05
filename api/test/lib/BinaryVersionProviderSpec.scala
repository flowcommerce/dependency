package io.flow.dependency.api.lib

import io.flow.dependency.v0.models.BinaryType
import util.DependencySpec

class BinaryVersionProviderSpec extends DependencySpec {

  "scala" in {
    val versions = DefaultBinaryVersionProvider.versions(BinaryType.Scala).map(_.value)
    versions.contains("2.11.7") must be(true)
    versions.contains("2.9.1.final") must be(true)
    versions.contains("0.11.7") must be(false)
  }

  "sbt" in {
    val versions = DefaultBinaryVersionProvider.versions(BinaryType.Sbt).map(_.value)
    versions.contains("0.13.8") must be(true)
    versions.contains("0.13.9") must be(true)
    versions.contains("0.0.1") must be(false)
  }

  "undefined" in {
    DefaultBinaryVersionProvider.versions(BinaryType.UNDEFINED("other")) must be(Nil)
  }

  "toVersion" in {
    DefaultBinaryVersionProvider.toVersion("Scala 2.11.7").map(_.value) must be(Some("2.11.7"))
    DefaultBinaryVersionProvider.toVersion("Scala 2.11.0-M4").map(_.value) must be(Some("2.11.0-M4"))
    DefaultBinaryVersionProvider.toVersion("Scala 2.9.1.final").map(_.value) must be(Some("2.9.1.final"))
    DefaultBinaryVersionProvider.toVersion("Scala License") must be(None)
  }

}
