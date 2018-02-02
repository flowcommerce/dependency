package io.flow.dependency.api.lib

import io.flow.dependency.v0.models.BinaryType
import org.specs2.mutable._

class DependencyHelperSpec extends Specification with Factories {

  lazy val org = makeOrganizationSummary()

  "crossBuildVersion for scala" in {
    Seq("2.10", "2.10.1", "2.10.1-RC1", "2.10.0-M3").map { tag =>
      DependencyHelper.crossBuildVersion(BinaryType.Scala, tag) must beEqualTo(
        Version("2.10", Seq(Tag.Semver(2, 10, 0)))
      )
    }

    Seq("2.11", "2.11.1", "2.11.1-RC1", "2.11.0-M3").map { tag =>
      DependencyHelper.crossBuildVersion(BinaryType.Scala, tag) must beEqualTo(
        Version("2.11", Seq(Tag.Semver(2, 11, 0)))
      )
    }
  }

  "crossBuildVersion for other binaries uses whole version" in {
    Seq("2.10", "2.10.1", "2.10.1-RC1", "2.10.0-M3").map { tag =>
      DependencyHelper.crossBuildVersion(BinaryType.UNDEFINED("other"), tag).value must beEqualTo(tag)
    }
  }

}
