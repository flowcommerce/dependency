import play.PlayImport.PlayKeys._

name := "dependency"

organization := "io.flow"

scalaVersion in ThisBuild := "2.11.8"

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
  .settings(commonSettings: _*)
  .settings(
    routesImport += "com.bryzek.dependency.v0.Bindables._",
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(
      jdbc,      
      "io.flow" %% "lib-postgresql" % "0.0.38",
      "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.18",
      "org.postgresql" % "postgresql" % "9.4.1212",
      "com.sendgrid"   %  "sendgrid-java" % "3.1.0"
    )
  )

lazy val www = project
  .in(file("www"))
  .dependsOn(generated, lib)
  .aggregate(generated, lib)
  .enablePlugins(PlayScala)
  .enablePlugins(NewRelic)
  .settings(commonSettings: _*)
  .settings(
    routesImport += "com.bryzek.dependency.v0.Bindables._",
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(
      "org.webjars" %% "webjars-play" % "2.5.0",
      "org.webjars" % "bootstrap" % "3.3.7",
      "org.webjars.bower" % "bootstrap-social" % "5.0.0",
      "org.webjars" % "font-awesome" % "4.7.0",
      "org.webjars" % "jquery" % "2.1.4"
    )
  )

val credsToUse = Option(System.getenv("ARTIFACTORY_USERNAME")) match {
  case None => Credentials(Path.userHome / ".ivy2" / ".artifactory")
  case _ => Credentials("Artifactory Realm","flow.artifactoryonline.com",System.getenv("ARTIFACTORY_USERNAME"),System.getenv("ARTIFACTORY_PASSWORD"))
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  name <<= name("dependency-" + _),
  libraryDependencies ++= Seq(
    "io.flow" %% "lib-play" % "0.2.6",
    specs2 % Test,
    "org.scalatestplus" %% "play" % "1.4.0" % "test"
  ),
  scalacOptions += "-feature",
  credentials += credsToUse,
  resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  resolvers += "Artifactory" at "https://flow.artifactoryonline.com/flow/libs-release/"
)
