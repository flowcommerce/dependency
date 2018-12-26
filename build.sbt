import play.sbt.PlayScala._

name := "dependency"

organization := "io.flow"

scalaVersion in ThisBuild := "2.12.8"

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
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-test" % "2.6.20",
      "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2",
      "org.specs2" %% "specs2-core" % "4.3.6",
      "com.typesafe.play" %% "play-specs2" % "2.6.20"
    )
  )


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
      "io.flow" %% "lib-util" % "0.1.4",
      "io.flow" %% "lib-postgresql-play26" % "0.0.96",
      "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.22",
      "org.postgresql" % "postgresql" % "42.2.5",
      "com.sendgrid"   %  "sendgrid-java" % "4.3.0",
      "io.flow" %% "lib-play-graphite-play26" % "0.0.63",
      "io.flow" %% "lib-log" % "0.0.51",
      "io.flow" %% "lib-test-utils" % "0.0.25" % Test      
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
      "org.webjars" % "font-awesome" % "5.6.1",
      "org.webjars" % "jquery" % "2.1.4",
      "io.flow" %% "lib-test-utils" % "0.0.25" % Test
    )
  )

val credsToUse = Option(System.getenv("ARTIFACTORY_USERNAME")) match {
  case None => Credentials(Path.userHome / ".ivy2" / ".artifactory")
  case _ => Credentials("Artifactory Realm","flow.jfrog.io",System.getenv("ARTIFACTORY_USERNAME"),System.getenv("ARTIFACTORY_PASSWORD"))
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  name ~= ("dependency-" + _),
  libraryDependencies ++= Seq(
    "io.flow" %% "lib-play-play26" % "0.5.25",
    "com.typesafe.play" %% "play-json-joda" % "2.6.10",
    "com.typesafe.play" %% "play-json" % "2.6.11"
  ),
  scalacOptions += "-feature",
  credentials += credsToUse,
  resolvers += "Artifactory" at "https://flow.jfrog.io/flow/libs-release/"
)
version := "0.6.89"
