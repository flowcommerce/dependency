name := "dependency"

organization := "io.flow"

ThisBuild / scalaVersion := "2.13.8"

lazy val allScalacOptions = Seq(
  "-feature",
  "-Xfatal-warnings",
  "-unchecked",
  "-Xcheckinit",
  "-Xlint:adapted-args",
  "-Ypatmat-exhaust-depth", "100", // Fixes: Exhaustivity analysis reached max recursion depth, not all missing cases are reported.
  "-Wconf:src=generated/.*:silent",
  "-Wconf:src=target/.*:silent" // silence the unused imports errors generated by the Play Routes
)

lazy val generated = project
  .in(file("generated"))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      ws
    ),
    scalacOptions ++= allScalacOptions
  )

lazy val lib = project
  .in(file("lib"))
  .dependsOn(generated)
  .aggregate(generated)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      playTest,
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0"
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
    javaAgents += "com.datadoghq" % "dd-java-agent" % "1.15.3",
    routesImport += "io.flow.dependency.v0.Bindables.Core._",
    routesImport += "io.flow.dependency.v0.Bindables.Models._",
    routesGenerator := InjectedRoutesGenerator,
    Test / testOptions += Tests.Argument("-oDF"),
    libraryDependencies ++= Seq(
      jdbc,
      ws,
      guice,
      "com.sendgrid" % "sendgrid-java" % "4.7.1",
      "io.flow" %% "lib-event-sync-play28" % "0.6.17",
      "io.flow" %% "lib-metrics-play28" % "1.0.57",
      "io.flow" %% "lib-log" % "0.1.95",
      "io.flow" %% "lib-usage-play28" % "0.2.22",
      "io.flow" %% "lib-test-utils-play28" % "0.2.1" % Test,
      "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.28",
      "org.postgresql" % "postgresql" % "42.6.0",
      "org.apache.commons" % "commons-text" % "1.10.0"
    ),
    scalacOptions ++= allScalacOptions
  )

lazy val www = project
  .in(file("www"))
  .dependsOn(generated, lib)
  .aggregate(generated, lib)
  .enablePlugins(PlayScala)
  .enablePlugins(SbtTwirl)
  .enablePlugins(NewRelic)
  .enablePlugins(JavaAgent)
  .enablePlugins(SbtWeb)
  .settings(commonSettings: _*)
  .settings(
    javaAgents += "com.datadoghq" % "dd-java-agent" % "1.15.3",
    routesImport += "io.flow.dependency.v0.Bindables.Core._",
    routesImport += "io.flow.dependency.v0.Bindables.Models._",
    routesGenerator := InjectedRoutesGenerator,
    Test / testOptions += Tests.Argument("-oD"),
    libraryDependencies ++= Seq(
      ws,
      guice,
      "org.webjars" %% "webjars-play" % "2.8.18",
      "org.webjars" % "bootstrap" % "3.4.1",
      "org.webjars" % "font-awesome" % "6.4.0",
      "org.webjars" % "jquery" % "3.6.4",
      "org.webjars.bower" % "bootstrap-social" % "5.1.1",
      "io.flow" %% "lib-test-utils-play28" % "0.2.1" % Test
    ),
    scalacOptions ++= allScalacOptions
  )

val credsToUse = Option(System.getenv("ARTIFACTORY_USERNAME")) match {
  case None => Credentials(Path.userHome / ".ivy2" / ".artifactory")
  case _ => Credentials("Artifactory Realm","flow.jfrog.io",System.getenv("ARTIFACTORY_USERNAME"),System.getenv("ARTIFACTORY_PASSWORD"))
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  name ~= ("dependency-" + _),
  libraryDependencies ++= Seq(
    "io.flow" %% "lib-play-play28" % "0.7.71",
    "com.typesafe.play" %% "play-json-joda" % "2.9.4",
    "com.typesafe.play" %% "play-json" % "2.9.4"
  ),
  scalacOptions ++= allScalacOptions,
  credentials += credsToUse,
  resolvers += "Artifactory" at "https://flow.jfrog.io/flow/libs-release/"
)
version := "0.8.43"
