package io.flow.dependency.api.lib

import io.flow.dependency.v0.models.BinaryType
import util.DependencySpec

class BinaryVersionProviderSpec extends DependencySpec {

  "scala" in {
    val versions = defaultBinaryVersionProvider.versions(BinaryType.Scala).map(_.value)
    versions.contains("2.11.7") must be(true)
    versions.contains("2.9.1.final") must be(true)
    versions.contains("0.11.7") must be(false)
  }

  "sbt" ignore { // TODO - https://flow.jfrog.io/flow/libs-release/org/scala-sbt/sbt/ does not contain sbt versions
    val versions = defaultBinaryVersionProvider.versions(BinaryType.Sbt).map(_.value)
    versions.contains("0.0.1") must be(false)
    versions.contains("1.3.0") must be(true)
  }

  "undefined" in {
    defaultBinaryVersionProvider.versions(BinaryType.UNDEFINED("other")) must be(Nil)
  }

  "toVersion" in {
    defaultBinaryVersionProvider.toVersion("Scala 2.11.7").map(_.value) must be(Some("2.11.7"))
    defaultBinaryVersionProvider.toVersion("Scala 2.11.0-M4").map(_.value) must be(Some("2.11.0-M4"))
    defaultBinaryVersionProvider.toVersion("Scala 2.9.1.final").map(_.value) must be(Some("2.9.1.final"))
    defaultBinaryVersionProvider.toVersion("Scala License") must be(None)
  }

}
