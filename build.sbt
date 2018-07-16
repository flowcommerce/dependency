import play.sbt.PlayScala._

name := "dependency"

organization := "io.flow"

scalaVersion in ThisBuild := "2.12.6"

lazy val generated = project
  .in(file("generated"))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      ws
    )
  )

lazy val lib = project
  .in(file("lib"))
  .dependsOn(generated)
  .aggregate(generated)
  .settings(commonSettings: _*)

lazy val api = project
  .in(file("api"))
  .dependsOn(generated, lib)
  .aggregate(generated, lib)
  .enablePlugins(PlayScala)
  .enablePlugins(NewRelic)
  .enablePlugins(JavaAppPackaging, JavaAgent)
  .settings(commonSettings: _*)
  .settings(
    javaAgents += "org.aspectj" % "aspectjweaver" % "1.8.13",
    javaOptions in Universal += "-Dorg.aspectj.tracing.factory=default",
    javaOptions in Test += "-Dkamon.modules.kamon-system-metrics.auto-start=false",
    javaOptions in Test += "-Dkamon.show-aspectj-missing-warning=no",
    routesImport += "io.flow.dependency.v0.Bindables.Core._",
    routesImport += "io.flow.dependency.v0.Bindables.Models._",
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(
      jdbc,
      ws,
      guice,
      "io.flow" %% "lib-dependency" % "SNAPSHOT",
      "io.flow" %% "lib-util" % "0.1.0",
      "io.flow" %% "lib-postgresql-play26" % "0.0.84",
      "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.22",
      "org.postgresql" % "postgresql" % "42.2.4",
      "com.sendgrid"   %  "sendgrid-java" % "4.2.1",
      "io.flow" %% "lib-play-graphite-play26" % "0.0.43"
    )
  )

lazy val www = project
  .in(file("www"))
  .dependsOn(generated, lib)
  .aggregate(generated, lib)
  .enablePlugins(PlayScala)
  .enablePlugins(NewRelic)
  .enablePlugins(SbtWeb)
  .settings(commonSettings: _*)
  .settings(
    routesImport += "io.flow.dependency.v0.Bindables.Core._",
    routesImport += "io.flow.dependency.v0.Bindables.Models._",
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(
      ws,
      guice,
      "org.webjars" %% "webjars-play" % "2.6.3",
      "org.webjars" % "bootstrap" % "3.3.7",
      "org.webjars.bower" % "bootstrap-social" % "5.1.1",
      "org.webjars" % "font-awesome" % "5.2.0",
      "org.webjars" % "jquery" % "2.1.4"
    )
  )

val credsToUse = Option(System.getenv("ARTIFACTORY_USERNAME")) match {
  case None => Credentials(Path.userHome / ".ivy2" / ".artifactory")
  case _ => Credentials("Artifactory Realm","flow.jfrog.io",System.getenv("ARTIFACTORY_USERNAME"),System.getenv("ARTIFACTORY_PASSWORD"))
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  name ~= ("dependency-" + _),
  libraryDependencies ++= Seq(
    "io.flow" %% "lib-play-play26" % "0.5.1",
    "com.typesafe.play" %% "play-json-joda" % "2.6.9",
    "com.typesafe.play" %% "play-json" % "2.6.9",
    "io.flow" %% "lib-test-utils" % "0.0.18" % Test
  ),
  scalacOptions += "-feature",
  credentials += credsToUse,
  resolvers += "Artifactory" at "https://flow.jfrog.io/flow/libs-release/"
)
version := "0.6.1"

