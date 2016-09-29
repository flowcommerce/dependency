package com.bryzek.dependency.api.lib

import com.bryzek.dependency.v0.models.BinaryForm
import org.specs2.mutable._

class ProjectPluginsSbtScalaParserSpec extends Specification with Factories {

  lazy val projectSummary = makeProjectSummary()

  "empty" should {

    val contents = """
// Comment to get more information during initialization
logLevel := Level.Warn
"""

    "parse dependencies" in {
      val result = ProjectPluginsSbtScalaParser(projectSummary, "test.sbt", contents)
      result.resolverUris must beEqualTo(Nil)
      result.plugins must beEqualTo(Nil)
    }

  }

  "with resolver" should {

    val contents = """
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
"""

    "parse dependencies" in {
      val result = ProjectPluginsSbtScalaParser(projectSummary, "test.sbt", contents)
      result.resolverUris must beEqualTo(Seq("http://repo.typesafe.com/typesafe/releases/"))
      result.plugins must beEqualTo(Nil)
    }

  }

  "with resolver and plugins" should {

    val contents = """
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.3")

resolvers += Resolver.url("scoverage-bintray", url("https://dl.bintray.com/sksamuel/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "1.0.1")
"""

    "parse dependencies" in {
      val result = ProjectPluginsSbtScalaParser(projectSummary, "test.sbt", contents)
      result.resolverUris must beEqualTo(
        Seq(
          "http://repo.typesafe.com/typesafe/releases/",
          "https://dl.bintray.com/sksamuel/sbt-plugins/"
        )
      )
      result.plugins must beEqualTo(
        Seq(
          Artifact(projectSummary, "test.sbt", "com.typesafe.play", "sbt-plugin", "2.4.3", false),
          Artifact(projectSummary, "test.sbt", "org.scoverage", "sbt-scoverage", "1.0.1", true)
        )
      )
    }

  }

}
