name := "dependency"

organization := "io.flow"

scalaVersion in ThisBuild := "2.13.3"

lazy val generated = project
  .in(file("generated"))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      ws,
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.7.1" cross CrossVersion.full),
      "com.github.ghik" %% "silencer-lib" % "1.7.1" % Provided cross CrossVersion.full
    ),
    // silence all warnings on autogenerated files
    flowGeneratedFiles ++= Seq(
      "app/.*".r,
    ),
    // Make sure you only exclude warnings for the project directories, i.e. make builds reproducible
    scalacOptions += s"-P:silencer:sourceRoots=${baseDirectory.value.getCanonicalPath}",
  )

lazy val lib = project
  .in(file("lib"))
  .dependsOn(generated)
  .aggregate(generated)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-test" % "2.8.7",
      "com.typesafe.play" %% "play-specs2" % "2.8.7",
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0",
      "org.specs2" %% "specs2-core" % "4.10.6",
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
    javaAgents += "io.kamon" % "kanela-agent" % "1.0.7",
    routesImport += "io.flow.dependency.v0.Bindables.Core._",
    routesImport += "io.flow.dependency.v0.Bindables.Models._",
    routesGenerator := InjectedRoutesGenerator,
    testOptions in Test += Tests.Argument("-oDF"),
    libraryDependencies ++= Seq(
      jdbc,
      ws,
      guice,
      "com.sendgrid" % "sendgrid-java" % "4.7.1",
      "io.flow" %% "lib-event-sync-play28" % "0.5.17",
      "io.flow" %% "lib-play-graphite-play28" % "0.1.81",
      "io.flow" %% "lib-log" % "0.1.31",
      "io.flow" %% "lib-usage-play28" % "0.1.50",
      "io.flow" %% "lib-test-utils-play28" % "0.1.21" % Test,
      "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.24",
      "org.postgresql" % "postgresql" % "42.2.18",
      "org.apache.commons" % "commons-text" % "1.9",
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.7.1" cross CrossVersion.full),
      "com.github.ghik" %% "silencer-lib" % "1.7.1" % Provided cross CrossVersion.full
    ),
    // silence all warnings on autogenerated files
    flowGeneratedFiles ++= Seq(
      "target/*".r,
      "app/generated/.*".r,
      "app/db/generated/.*".r,
    ),
    // Make sure you only exclude warnings for the project directories, i.e. make builds reproducible
    scalacOptions += s"-P:silencer:sourceRoots=${baseDirectory.value.getCanonicalPath}",
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
    testOptions in Test += Tests.Argument("-oD"),
    libraryDependencies ++= Seq(
      ws,
      guice,
      "org.webjars" %% "webjars-play" % "2.8.0",
      "org.webjars" % "bootstrap" % "3.4.1",
      "org.webjars" % "font-awesome" % "5.15.2",
      "org.webjars" % "jquery" % "3.5.1",
      "org.webjars.bower" % "bootstrap-social" % "5.1.1",
      "io.flow" %% "lib-test-utils-play28" % "0.1.21" % Test,
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.7.1" cross CrossVersion.full),
      "com.github.ghik" %% "silencer-lib" % "1.7.1" % Provided cross CrossVersion.full
    ),
    // silence all warnings on autogenerated files
    flowGeneratedFiles ++= Seq(
      "target/*".r,
    ),
    // Make sure you only exclude warnings for the project directories, i.e. make builds reproducible
    scalacOptions += s"-P:silencer:sourceRoots=${baseDirectory.value.getCanonicalPath}",
  )

val credsToUse = Option(System.getenv("ARTIFACTORY_USERNAME")) match {
  case None => Credentials(Path.userHome / ".ivy2" / ".artifactory")
  case _ => Credentials("Artifactory Realm","flow.jfrog.io",System.getenv("ARTIFACTORY_USERNAME"),System.getenv("ARTIFACTORY_PASSWORD"))
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  name ~= ("dependency-" + _),
  libraryDependencies ++= Seq(
    "io.flow" %% "lib-play-play28" % "0.6.28",
    "com.typesafe.play" %% "play-json-joda" % "2.9.2",
    "com.typesafe.play" %% "play-json" % "2.9.2"
  ),
  scalacOptions += "-feature",
  credentials += credsToUse,
  resolvers += "Artifactory" at "https://flow.jfrog.io/flow/libs-release/"
)
version := "0.7.70"
