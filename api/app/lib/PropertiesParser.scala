package io.flow.dependency.api.lib

import io.flow.dependency.v0.models.ProjectSummary
import io.flow.log.RollbarLogger

case class PropertiesParser(
  override val project: ProjectSummary,
  override val path: String,
  contents: String,
  override val logger: RollbarLogger,
) extends SimpleScalaParser {

  private[this] lazy val properties: Map[String, String] = {
    val internal = scala.collection.mutable.Map[String, String]()
    lines.foreach { line =>
      line.split("=").map(_.trim).toList match {
        case key :: value :: Nil => {
          internal += (key -> value)
        }
        case _ => {}
      }
    }
    internal.toMap
  }

  def get(name: String): Option[String] = {
    properties.get(name)
  }

}
