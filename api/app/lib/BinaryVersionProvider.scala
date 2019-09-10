package io.flow.dependency.api.lib

import io.flow.dependency.v0.models.BinaryType
import io.flow.log.RollbarLogger
import io.flow.util.Version
import org.apache.commons.lang3.StringUtils



trait BinaryVersionProvider {

  /**
    * Returns the versions for this binary, fetching them from
    * appropriate remote locations.
    */
  def versions(binary: BinaryType): Seq[Version]

}

@javax.inject.Singleton
case class DefaultBinaryVersionProvider @javax.inject.Inject()(
  logger: RollbarLogger
) extends BinaryVersionProvider {

  private[this] val ScalaUrl = "https://www.scala-lang.org/download/all.html"
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
        if (!name.startsWith("tst-")) {
          logger.withKeyValue("binary_name", name).warn(s"Do not know how to find versions for the programming binary")
        }
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
