name := "dependency"

organization := "io.flow"

ThisBuild / scalaVersion := "2.13.15"

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
ThisBuild / libraryDependencySchemes += "org.scoverage" %% "sbt-scoverage" % VersionScheme.Always

lazy val allScalacOptions = Seq(
  "-feature",
  "-Xfatal-warnings",
  "-unchecked",
  "-Xcheckinit",
  "-Xlint:adapted-args",
  "-Ypatmat-exhaust-depth",
  "100", // Fixes: Exhaustivity analysis reached max recursion depth, not all missing cases are reported.
  "-Wconf:src=generated/.*:silent",
  "-Wconf:src=target/.*:silent", // silence the unused imports errors generated by the Play Routes
)

lazy val generated = project
  .in(file("generated"))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      ws,
      "joda-time" % "joda-time" % "2.12.7",
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
      playTest,
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0",
    ),
  )

lazy val api = project
  .in(file("api"))
  .dependsOn(generated, lib)
  .aggregate(generated, lib)
  .enablePlugins(PlayScala)
  .enablePlugins(JavaAppPackaging, JavaAgent)
  .settings(commonSettings: _*)
  .settings(
    javaAgents += "com.datadoghq" % "dd-java-agent" % "1.44.1",
    routesImport += "io.flow.dependency.v0.Bindables.Core._",
    routesImport += "io.flow.dependency.v0.Bindables.Models._",
    routesGenerator := InjectedRoutesGenerator,
    Test / testOptions += Tests.Argument("-oDF"),
    libraryDependencies ++= Seq(
      jdbc,
      ws,
      "org.projectlombok" % "lombok" % "1.18.36" % "provided",
      "com.sendgrid" % "sendgrid-java" % "4.7.1",
      "io.flow" %% "lib-event-sync-play29" % "0.6.82",
      "io.flow" %% "lib-metrics-play29" % "1.1.6",
      "io.flow" %% "lib-log-play29" % "0.2.33",
      "io.flow" %% "lib-usage-play29" % "0.2.67",
      "io.flow" %% "lib-test-utils-play29" % "0.2.45" % Test,
      "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.29",
      "org.postgresql" % "postgresql" % "42.7.5",
      "org.apache.commons" % "commons-text" % "1.13.0",
    ),
    scalacOptions ++= allScalacOptions,
  )

lazy val www = project
  .in(file("www"))
  .dependsOn(generated, lib)
  .aggregate(generated, lib)
  .enablePlugins(PlayScala)
  .enablePlugins(SbtTwirl)
  .enablePlugins(JavaAgent)
  .enablePlugins(SbtWeb)
  .settings(commonSettings: _*)
  .settings(
    javaAgents += "com.datadoghq" % "dd-java-agent" % "1.44.1",
    routesImport += "io.flow.dependency.v0.Bindables.Core._",
    routesImport += "io.flow.dependency.v0.Bindables.Models._",
    routesGenerator := InjectedRoutesGenerator,
    Test / testOptions += Tests.Argument("-oD"),
    libraryDependencies ++= Seq(
      ws,
      "org.projectlombok" % "lombok" % "1.18.36" % "provided",
      "org.webjars" %% "webjars-play" % "3.0.2",
      "org.webjars" % "bootstrap" % "3.3.7",
      "org.webjars" % "jquery" % "3.7.1",
      "org.webjars" % "bootstrap-social" % "5.0.0",
      "io.flow" %% "lib-test-utils-play29" % "0.2.45" % Test,
    ),
    scalacOptions ++= allScalacOptions,
  )

val credsToUse = Option(System.getenv("ARTIFACTORY_USERNAME")) match {
  case None => Credentials(Path.userHome / ".ivy2" / ".artifactory")
  case _ =>
    Credentials(
      "Artifactory Realm",
      "flow.jfrog.io",
      System.getenv("ARTIFACTORY_USERNAME"),
      System.getenv("ARTIFACTORY_PASSWORD"),
    )
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  scalafmtOnCompile := true,
  name ~= ("dependency-" + _),
  libraryDependencies ++= Seq(
    "io.flow" %% "lib-play-play29" % "0.8.10",
    "com.typesafe.play" %% "play-json-joda" % "2.10.6",
  ),
  Test / javaOptions ++= Seq(
    "--add-exports=java.base/sun.security.x509=ALL-UNNAMED",
    "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED",
  ),
  scalacOptions ++= allScalacOptions,
  credentials += credsToUse,
  resolvers += "Artifactory" at "https://flow.jfrog.io/flow/libs-release/",
  coverageExcludedFiles := ".*\\/*generated*\\/.*",
  coverageDataDir := file("target/scala-2.13"),
  coverageHighlighting := true,
  coverageFailOnMinimum := true,
  coverageMinimumStmtTotal := 36,
  coverageMinimumBranchTotal := 36,
)
version := "0.8.43"
