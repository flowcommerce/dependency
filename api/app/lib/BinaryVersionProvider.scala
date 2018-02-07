package io.flow.dependency.api.lib

import io.flow.dependency.v0.models.BinaryType
import org.apache.commons.lang3.StringUtils
import play.api.Logger


trait BinaryVersionProvider {

  /**
    * Returns the versions for this binary, fetching them from
    * appropriate remote locations.
    */
  def versions(binary: BinaryType): Seq[Version]

}

object DefaultBinaryVersionProvider extends BinaryVersionProvider {

  private[this] val ScalaUrl = "http://www.scala-lang.org/download/all.html"
  private[this] val SbtUrl = "https://dl.bintray.com/sbt/native-packages/sbt/"

  override def versions(
    binary: BinaryType
  ) : Seq[Version] = {
    binary match {
      case BinaryType.Scala => {
        fetchScalaVersions()
      }
      case BinaryType.Sbt => {
        fetchSbtVersions()
      }
      case BinaryType.UNDEFINED(name) => {
        Logger.warn(s"Do not know how to find versions for the programming binary[$name]")
        Nil
      }
    }
  }

  def fetchScalaVersions(): Seq[Version] = {
    RemoteDirectory.fetch(ScalaUrl) { name =>
      name.toLowerCase.startsWith("scala ")
    }.files.flatMap { toVersion(_) }
  }

  def fetchSbtVersions(): Seq[Version] = {
    RemoteDirectory.fetch(SbtUrl)().directories.flatMap { dir =>
      toVersion(StringUtils.stripEnd(dir, "/"))
    }
  }

  def toVersion(value: String): Option[Version] = {
    val tag = Version(
      StringUtils.stripStart(
        StringUtils.stripStart(value, "scala"),
        "Scala"
      ).trim
    )
    tag.major match {
      case None => None
      case Some(_) => Some(tag)
    }
  }

}
