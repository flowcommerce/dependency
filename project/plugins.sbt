// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.9.6")

addSbtPlugin("com.typesafe.play" % "sbt-twirl" % "1.6.8")

addSbtPlugin("com.github.sbt" % "sbt-javaagent" % "0.1.8")

addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.4.1")

resolvers += "Flow Plugins" at "https://flow.jfrog.io/flow/plugins-release/"

addSbtPlugin("io.flow" % "sbt-flow-linter" % "0.0.72")

// Resolve scala-xml version dependency mismatch, see https://github.com/sbt/sbt/issues/7007
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always,
)

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.5")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.3.0")
