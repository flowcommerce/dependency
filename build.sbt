name := "dependency"

organization := "io.flow"

ThisBuild / scalaVersion := "2.13.6"

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
    javaAgents += "com.datadoghq" % "dd-java-agent" % "0.88.0",
    routesImport += "io.flow.dependency.v0.Bindables.Core._",
    routesImport += "io.flow.dependency.v0.Bindables.Models._",
    routesGenerator := InjectedRoutesGenerator,
    Test / testOptions += Tests.Argument("-oDF"),
    libraryDependencies ++= Seq(
      jdbc,
      ws,
      guice,
      "com.sendgrid" % "sendgrid-java" % "4.7.6",
      "io.flow" %% "lib-event-sync-play28" % "0.5.44",
      "io.flow" %% "lib-metrics-play28" % "1.0.8",
      "io.flow" %% "lib-log" % "0.1.51",
      "io.flow" %% "lib-usage-play28" % "0.1.77",
      "io.flow" %% "lib-test-utils-play28" % "0.1.50" % Test,
      "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.25",
      "org.postgresql" % "postgresql" % "42.2.24",
      "org.apache.commons" % "commons-text" % "1.9"
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
    javaAgents += "com.datadoghq" % "dd-java-agent" % "0.88.0",
    routesImport += "io.flow.dependency.v0.Bindables.Core._",
    routesImport += "io.flow.dependency.v0.Bindables.Models._",
    routesGenerator := InjectedRoutesGenerator,
    Test / testOptions += Tests.Argument("-oD"),
    libraryDependencies ++= Seq(
      ws,
      guice,
      "org.webjars" %% "webjars-play" % "2.8.8-1",
      "org.webjars" % "bootstrap" % "3.4.1",
      "org.webjars" % "font-awesome" % "5.15.4",
      "org.webjars" % "jquery" % "3.6.0",
      "org.webjars.bower" % "bootstrap-social" % "5.1.1",
      "io.flow" %% "lib-test-utils-play28" % "0.1.50" % Test
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
    "io.flow" %% "lib-play-play28" % "0.7.2",
    "com.typesafe.play" %% "play-json-joda" % "2.9.2",
    "com.typesafe.play" %% "play-json" % "2.9.2"
  ),
  scalacOptions ++= allScalacOptions,
  credentials += credsToUse,
  resolvers += "Artifactory" at "https://flow.jfrog.io/flow/libs-release/"
)
version := "0.8.43"
