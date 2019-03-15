package io.flow.dependency.api.lib

import io.flow.dependency.v0.models.BinaryType
import db.ProjectBinaryForm
import util.DependencySpec

class BuildSbtScalaParserSpec extends DependencySpec {

  lazy val projectSummary = makeProjectSummary()

  "simple library with no dependencies" should {

    val contents = """
name := "lib-play"

lazy val root = project
  .in(file("."))
"""

    "parse dependencies" in {
      val result = BuildSbtScalaParser(projectSummary, "test.sbt", contents, logger)
      result.binaries must be(Nil)
      result.libraries must be(Nil)
    }

  }

  "single project w/ dependencies" should {

    val contents = """
import play.PlayImport.PlayKeys._

organization := "io.flow"

scalaVersion in ThisBuild := "2.11.7"

crossScalaVersions := Seq("2.11.7")

lazy val root = project
  .in(file("."))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      ws,
      "io.flow" %% "lib-play-postgresql" % "0.0.1-SNAPSHOT",
      "org.postgresql" % "postgresql" % "9.4-1202-jdbc42"
    )
)
"""

    "parse dependencies" in {
      val result = BuildSbtScalaParser(projectSummary, "test.sbt", contents, logger)
      result.binaries must contain theSameElementsAs Seq(ProjectBinaryForm(projectSummary.id, BinaryType.Scala, "2.11.7", "test.sbt"))
      result.libraries must be(
        Seq(
          Artifact(projectSummary, "test.sbt", "io.flow", "lib-play-postgresql", "0.0.1-SNAPSHOT", true),
          Artifact(projectSummary, "test.sbt", "org.postgresql", "postgresql", "9.4-1202-jdbc42", false)
        )
      )
    }
 }

  "dependencies w/ comments" should {

    val contents = """
lazy val root = project
  .in(file("."))
  .settings(
    libraryDependencies ++= Seq(
      ws,
      "io.flow" %% "lib-play-postgresql" % "0.0.1-SNAPSHOT" % Test, // Foo
      "org.postgresql" % "postgresql" % "9.4-1202-jdbc42" // Bar
    )
)
"""

    "parse dependencies" in {
      val result = BuildSbtScalaParser(projectSummary, "test.sbt", contents, logger)
      result.binaries must be(Nil)
      result.libraries must contain theSameElementsAs Seq(
        Artifact(projectSummary, "test.sbt", "io.flow", "lib-play-postgresql", "0.0.1-SNAPSHOT", true),
        Artifact(projectSummary, "test.sbt", "org.postgresql", "postgresql", "9.4-1202-jdbc42", false)
      )
    }
  }

  "multi project build w/ duplicates" should {

    val contents = """
lazy val api = project
  .in(file("api"))
  .settings(
    libraryDependencies ++= Seq(
      ws,
      "io.flow" %% "lib-play-postgresql" % "0.0.1-SNAPSHOT",
      "org.postgresql" % "postgresql" % "9.4-1202-jdbc42"
    )
)

lazy val www = project
  .in(file("api"))
  .settings(
    libraryDependencies ++= Seq(
      "io.flow" %% "lib-play-postgresql" % "0.0.2-SNAPSHOT",
      "org.postgresql" % "postgresql" % "9.4-1202-jdbc42"
    )
)
"""

    "parse dependencies" in {
      val result = BuildSbtScalaParser(projectSummary, "test.sbt", contents, logger)
      result.binaries must be(Nil)
      result.libraries must contain theSameElementsAs Seq(
        Artifact(projectSummary, "test.sbt", "io.flow", "lib-play-postgresql", "0.0.1-SNAPSHOT", true),
        Artifact(projectSummary, "test.sbt", "io.flow", "lib-play-postgresql", "0.0.2-SNAPSHOT", true),
        Artifact(projectSummary, "test.sbt", "org.postgresql", "postgresql", "9.4-1202-jdbc42", false)
      )
    }
  }

  "library with variable version names" in {
    val contents = """
val avroVersion = "1.7.7"
lazy val akkaVersion = "2.3.4"

lazy val avro = project
  .in(file("avro"))
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.avro"   % "avro"              % avroVersion,
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
    )
  )
"""
    val result = BuildSbtScalaParser(projectSummary, "test.sbt", contents, logger)
    result.binaries must be(Nil)
    result.libraries must contain theSameElementsAs Seq(
      Artifact(projectSummary, "test.sbt", "com.typesafe.akka", "akka-cluster", "2.3.4", true),
      Artifact(projectSummary, "test.sbt", "com.typesafe.akka", "akka-testkit", "2.3.4", true),
      Artifact(projectSummary, "test.sbt", "org.apache.avro", "avro", "1.7.7", false)
    )
  }

  "library for Test" in {
    val contents = """
  libraryDependencies ++= Seq(
    specs2 % Test,
    "org.scalatest" %% "scalatest" % "2.2.0" % Test
  )
"""

    val result = BuildSbtScalaParser(projectSummary, "test.sbt", contents, logger)
    result.binaries must be(Nil)
    result.libraries must contain theSameElementsAs Seq(
      Artifact(projectSummary, "test.sbt", "org.scalatest", "scalatest", "2.2.0", true)
    )
  }


  "with inline resolvers" should {

    val contents = """
lazy val commonSettings: Seq[Def.Setting[_]] = Seq(
  organization := "com.cavellc",
  name <<= name("cave-" + _),
  version := "git describe --tags --dirty --always".!!.stripPrefix("v").trim,
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  libraryDependencies ++= Seq(
    "io.dropwizard.metrics" % "metrics-core" % "3.1.0",
    "io.dropwizard.metrics" % "metrics-jvm" % "3.1.0",
    "org.scalatest" %% "scalatest" % "2.1.2" % "test"
  )
)
"""

    "parse dependencies" in {
      val result = BuildSbtScalaParser(projectSummary, "test.sbt", contents, logger)
      result.binaries must be(Nil)
      result.libraries must contain theSameElementsAs Seq(
        Artifact(projectSummary, "test.sbt", "io.dropwizard.metrics", "metrics-core", "3.1.0", false),
        Artifact(projectSummary, "test.sbt", "io.dropwizard.metrics", "metrics-jvm", "3.1.0", false),
        Artifact(projectSummary, "test.sbt", "org.scalatest", "scalatest", "2.1.2", true)
      )
      result.resolverUris must contain theSameElementsAs Seq("http://repo.typesafe.com/typesafe/releases/")
    }

  }

  "with inline seq" should {
    val contents = """
    libraryDependencies ++= Seq(ws, "com.typesafe.play" %% "play-json" % "2.2.2"),
"""

    "parse dependencies" in {
      val result = BuildSbtScalaParser(projectSummary, "test.sbt", contents, logger)
      result.binaries must be(Nil)
      result.libraries must contain theSameElementsAs Seq(
        Artifact(projectSummary, "test.sbt", "com.typesafe.play", "play-json", "2.2.2", true)
      )
      result.resolverUris must be(Nil)
    }
  }

 "with multiple lines" should {
    val contents = """
lazy val rules = project.settings(
  libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % V.scalafixVersion,
)

lazy val input = project.settings(
  libraryDependencies += "io.flow" %% "lib-play-play26" % "0.5.28",
)

lazy val output = project.settings(
  libraryDependencies += "io.flow" %% "lib-play-play26" % "0.5.28",
)

lazy val tests = project.settings(
  libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % V.scalafixVersion % Test cross CrossVersion.full,
)
"""

    "parse dependencies" in {
      val result = BuildSbtScalaParser(projectSummary, "test.sbt", contents, logger)
      result.binaries must be(Nil)
      result.libraries must contain theSameElementsAs Seq(
        Artifact(projectSummary, "test.sbt", "ch.epfl.scala", "scalafix-core", "V.scalafixVersion", true),
        Artifact(projectSummary, "test.sbt", "io.flow", "lib-play-play26", "0.5.28", true),
        Artifact(projectSummary, "test.sbt", "ch.epfl.scala", "scalafix-testkit", "V.scalafixVersion", false),
      )
      result.resolverUris must be(Nil)
    }
  }

  "non-library dependencies" should {
    val contents = """
    scalafixDependencies += "io.flow" %% "scalafix-rules" % "0.0.1"
"""

    "parse dependencies" in {
      val result = BuildSbtScalaParser(projectSummary, "test.sbt", contents, logger)
      result.binaries must be(Nil)
      result.libraries must contain theSameElementsAs Seq(
        Artifact(projectSummary, "test.sbt", "io.flow", "scalafix-rules", "0.0.1", true),
      )
      result.resolverUris must be(Nil)
    }
  }

}
