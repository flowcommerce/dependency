package io.flow.dependency.api.lib

import util.DependencySpec

class RemoteVersionsSpec extends DependencySpec {

  "makeUrl" in {
    RemoteVersions.makeUrls("https://oss.sonatype.org/content/repositories/snapshots/", "com.github.tototoshi") must be(
      Seq(
        "https://oss.sonatype.org/content/repositories/snapshots/com/github/tototoshi",
        "https://oss.sonatype.org/content/repositories/snapshots/com.github.tototoshi"
      )
    )
  }

  "crossBuildVersion" in {
    RemoteVersions.crossBuildVersion("scala-csv_2.11/").map(_.value) must be(Some("2.11"))
    RemoteVersions.crossBuildVersion("scala-csv_2.11").map(_.value) must be(Some("2.11"))
    RemoteVersions.crossBuildVersion("scala-csv_2.10").map(_.value) must be(Some("2.10"))
    RemoteVersions.crossBuildVersion("scala-csv_2.9.3").map(_.value) must be(Some("2.9.3"))
    RemoteVersions.crossBuildVersion("scala-csv_2.9.3-dev").map(_.value) must be(Some("2.9.3-dev"))

    RemoteVersions.crossBuildVersion("scala-csv") must be(None)
    RemoteVersions.crossBuildVersion("scala-csv_foo") must be(None)
  }

}
