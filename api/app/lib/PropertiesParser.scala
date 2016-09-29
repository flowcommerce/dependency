package com.bryzek.dependency.api.lib

import com.bryzek.dependency.v0.models.ProjectSummary

case class PropertiesParser(
  override val project: ProjectSummary,
  override val path: String,
  contents: String
) extends SimpleScalaParser {

  private[this] lazy val properties: Map[String, String] = {
    var internal = scala.collection.mutable.Map[String, String]()
    lines.foreach { line =>
      line.split("=").map(_.trim).toList match {
        case key :: value :: Nil => {
          internal += (key -> value)
        }
        case _ => {
        }
      }
    }
    internal.toMap
  }

  def get(name: String): Option[String] = {
    properties.get(name)
  }

}
