package io.flow.dependency.api.lib

import io.flow.dependency.v0.models.ProjectSummary
import io.flow.log.RollbarLogger

/**
  * Takes the contents of a project/plugins.sbt file and parses it, providing
  * access to its dependencies (repositories and plugins).
  */
case class ProjectPluginsSbtScalaParser(
  override val project: ProjectSummary,
  override val path: String,
  contents: String,
  override val logger: RollbarLogger
) extends SimpleScalaParser {

  val plugins: Seq[Artifact] = parseLibraries()

  val resolverUris: Seq[String] = {
    lines.
      filter(_.startsWith("resolvers ")).
      flatMap(toResolver(_)).
      distinct.sortBy(_.toLowerCase)
  }

  def toResolver(line: String): Option[String] = {
    val atIndex = line.indexOf(" at ")
    if (atIndex > 0) {
      Some(interpolate(line.substring(atIndex + 3).trim))
    } else {
      val urlIndex = line.indexOf("url(\"")
      if (urlIndex > 0) {
        val start = line.substring(urlIndex + 5).trim
        if (start.toLowerCase.startsWith("http")) {
          val endingIndex = start.indexOf(")")
          Some(interpolate(start.substring(0, endingIndex)))
        } else {
          toResolver(start)
        }
      } else {
        None
      }
    }
  }
}
