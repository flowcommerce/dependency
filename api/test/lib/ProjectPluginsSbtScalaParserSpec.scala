package io.flow.dependency.api.lib

import util.DependencySpec

class ProjectPluginsSbtScalaParserSpec extends DependencySpec {

  lazy val projectSummary = makeProjectSummary()

  "empty" should {

    val contents = """
// Comment to get more information during initialization
logLevel := Level.Warn
"""

    "parse dependencies" in {
      val result = ProjectPluginsSbtScalaParser(projectSummary, "test.sbt", contents, logger)
      result.resolverUris must be(Nil)
      result.plugins must be(Nil)
    }

  }

  "with resolver" should {

    val contents = """
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
"""

    "parse dependencies" in {
      val result = ProjectPluginsSbtScalaParser(projectSummary, "test.sbt", contents, logger)
      result.resolverUris must contain theSameElementsAs Seq("http://repo.typesafe.com/typesafe/releases/")
      result.plugins must be(Nil)
    }

  }

  "with resolver and plugins" should {

    val contents = """
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.3")

resolvers += Resolver.url("scoverage-bintray", url("https://dl.bintray.com/sksamuel/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.0.1")
"""

    "parse dependencies" in {
      val result = ProjectPluginsSbtScalaParser(projectSummary, "test.sbt", contents, logger)
      result.resolverUris must contain theSameElementsAs Seq(
        "http://repo.typesafe.com/typesafe/releases/",
        "https://dl.bintray.com/sksamuel/sbt-plugins/"
      )
      result.plugins must contain theSameElementsAs Seq(
        Artifact(projectSummary, "test.sbt", "com.typesafe.play", "sbt-plugin", "2.4.3", true, true),
        Artifact(projectSummary, "test.sbt", "org.scoverage", "sbt-scoverage", "1.0.1", true, true)
      )
    }

  }

}
