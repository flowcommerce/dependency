package io.flow.dependency.api.lib

import db.ProjectBinaryForm
import io.flow.dependency.v0.models.{BinaryType, ProjectSummary}
import io.flow.log.RollbarLogger

/** Takes the contents of a build.sbt file and parses it, providing access to its dependencies (libraries, binaries and
  * versions).
  */
case class BuildSbtScalaParser(
  override val project: ProjectSummary,
  override val path: String,
  contents: String,
  override val logger: RollbarLogger
) extends SimpleScalaParser {

  private lazy val pluginParser = ProjectPluginsSbtScalaParser(project, path, contents, logger)

  val resolverUris = pluginParser.resolverUris

  val libraries: Seq[Artifact] = parseLibraries()

  val binaries: Seq[ProjectBinaryForm] = {
    lines.filter(_.contains("scalaVersion")).flatMap { line =>
      line.split(":=").map(_.trim).toList match {
        case _ :: version :: Nil => {
          Some(
            ProjectBinaryForm(
              projectId = project.id,
              name = BinaryType.Scala,
              version = interpolate(version),
              path
            )
          )
        }
        case _ => {
          None
        }
      }
    }
  }.distinct.sortBy { l => (l.name.toString.toLowerCase, l.version) }

}
