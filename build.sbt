import play.sbt.PlayScala._

name := "dependency"

organization := "io.flow"

scalaVersion in ThisBuild := "2.12.10"

lazy val generated = project
  .in(file("generated"))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      ws,
      "com.github.ghik" %% "silencer-lib" % "1.4.2" % Provided,
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.4.2"),
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
      "com.typesafe.play" %% "play-test" % "2.6.20",
      "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2",
      "org.specs2" %% "specs2-core" % "4.8.3",
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
    javaAgents += "io.kamon" % "kanela-agent" % "1.0.4",
    routesImport += "io.flow.dependency.v0.Bindables.Core._",
    routesImport += "io.flow.dependency.v0.Bindables.Models._",
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(
      jdbc,
      ws,
      guice,
      "io.flow" %% "lib-util" % "0.1.37",
      "io.flow" %% "lib-akka" % "0.1.14",
      "io.flow" %% "lib-postgresql-play26" % "0.1.44",
      "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.23",
      "org.postgresql" % "postgresql" % "42.2.9",
      "com.sendgrid"   %  "sendgrid-java" % "4.4.1",
      "org.apache.commons" % "commons-text" % "1.8",
      "io.flow" %% "lib-play-graphite-play26" % "0.1.36",
      "io.flow" %% "lib-log" % "0.0.97",
      "io.flow" %% "lib-usage" % "0.1.15",
      "io.flow" %% "lib-test-utils" % "0.0.81" % Test,      
      "com.github.ghik" %% "silencer-lib" % "1.4.2" % Provided,
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.4.2"),
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
    libraryDependencies ++= Seq(
      ws,
      guice,
      "org.webjars" %% "webjars-play" % "2.8.0",
      "org.webjars" % "bootstrap" % "3.3.7",
      "org.webjars.bower" % "bootstrap-social" % "5.1.1",
      "org.webjars" % "font-awesome" % "5.12.0",
      "org.webjars" % "jquery" % "2.1.4",
      "io.flow" %% "lib-test-utils" % "0.0.81" % Test,
      "com.github.ghik" %% "silencer-lib" % "1.4.2" % Provided,
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.4.2"),
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
    "io.flow" %% "lib-play-play26" % "0.5.92",
    "com.typesafe.play" %% "play-json-joda" % "2.8.1",
    "com.typesafe.play" %% "play-json" % "2.8.1"
  ),
  scalacOptions += "-feature",
  credentials += credsToUse,
  resolvers += "Artifactory" at "https://flow.jfrog.io/flow/libs-release/"
)
version := "0.7.26"
