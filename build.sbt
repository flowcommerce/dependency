name := "dependency"

organization := "io.flow"

ThisBuild / scalaVersion := "2.13.5"

lazy val allScalacOptions = Seq(
  "-feature",
  "-Xfatal-warnings",
  "-unchecked",
  "-Xcheckinit",
  "-Xlint:adapted-args",
  "-Ypatmat-exhaust-depth", "100", // Fixes: Exhaustivity analysis reached max recursion depth, not all missing cases are reported.
  "-Wconf:src=generated/.*:silent",
  "-Wconf:src=target/.*:silent", // silence the unused imports errors generated by the Play Routes
)

lazy val generated = project
  .in(file("generated"))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      ws,
    ),
    scalacOptions ++= allScalacOptions,
  )

lazy val lib = project
  .in(file("lib"))
  .dependsOn(generated)
  .aggregate(generated)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-test" % "2.8.8",
      "com.typesafe.play" %% "play-specs2" % "2.8.8",
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0",
      "org.specs2" %% "specs2-core" % "4.11.0",
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
    javaAgents += "io.kamon" % "kanela-agent" % "1.0.10",
    routesImport += "io.flow.dependency.v0.Bindables.Core._",
    routesImport += "io.flow.dependency.v0.Bindables.Models._",
    routesGenerator := InjectedRoutesGenerator,
    Test / testOptions += Tests.Argument("-oDF"),
    libraryDependencies ++= Seq(
      jdbc,
      ws,
      guice,
      "com.sendgrid" % "sendgrid-java" % "4.7.1",
      "io.flow" %% "lib-event-sync-play28" % "0.5.28",
      "io.flow" %% "lib-play-graphite-play28" % "0.1.94",
      "io.flow" %% "lib-log" % "0.1.38",
      "io.flow" %% "lib-usage-play28" % "0.1.61",
      "io.flow" %% "lib-test-utils-play28" % "0.1.33" % Test,
      "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.24",
      "org.postgresql" % "postgresql" % "42.2.20",
      "org.apache.commons" % "commons-text" % "1.9",
    ),
    scalacOptions ++= allScalacOptions,
  )

lazy val www = project
  .in(file("www"))
  .dependsOn(generated, lib)
  .aggregate(generated, lib)
  .enablePlugins(PlayScala)
  .enablePlugins(SbtTwirl)
  .enablePlugins(NewRelic)
  .enablePlugins(SbtWeb)
  .settings(commonSettings: _*)
  .settings(
    routesImport += "io.flow.dependency.v0.Bindables.Core._",
    routesImport += "io.flow.dependency.v0.Bindables.Models._",
    routesGenerator := InjectedRoutesGenerator,
    Test / testOptions += Tests.Argument("-oD"),
    libraryDependencies ++= Seq(
      ws,
      guice,
      "org.webjars" %% "webjars-play" % "2.8.0",
      "org.webjars" % "bootstrap" % "3.4.1",
      "org.webjars" % "font-awesome" % "5.15.2",
      "org.webjars" % "jquery" % "3.6.0",
      "org.webjars.bower" % "bootstrap-social" % "5.1.1",
      "io.flow" %% "lib-test-utils-play28" % "0.1.33" % Test,
    ),
    scalacOptions ++= allScalacOptions,
  )

val credsToUse = Option(System.getenv("ARTIFACTORY_USERNAME")) match {
  case None => Credentials(Path.userHome / ".ivy2" / ".artifactory")
  case _ => Credentials("Artifactory Realm","flow.jfrog.io",System.getenv("ARTIFACTORY_USERNAME"),System.getenv("ARTIFACTORY_PASSWORD"))
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  name ~= ("dependency-" + _),
  libraryDependencies ++= Seq(
    "io.flow" %% "lib-play-play28" % "0.6.38",
    "com.typesafe.play" %% "play-json-joda" % "2.9.2",
    "com.typesafe.play" %% "play-json" % "2.9.2"
  ),
  scalacOptions ++= allScalacOptions,
  credentials += credsToUse,
  resolvers += "Artifactory" at "https://flow.jfrog.io/flow/libs-release/"
)
