package io.flow.dependency.api.lib

import db.ProjectBinaryForm
import io.flow.dependency.v0.models.{BinaryType, Project}
import io.flow.util.{Tag, Version}
import scala.concurrent.{ExecutionContext, Future}

case class Dependencies(
  binaries: Option[Seq[ProjectBinaryForm]] = None,
  libraries: Option[Seq[Artifact]] = None,
  resolverUris: Option[Seq[String]] = None,
  plugins: Option[Seq[Artifact]] = None
) {

  def librariesAndPlugins: Option[Seq[Artifact]] = {
    (libraries, plugins) match {
      case (None, None) => None
      case (Some(lib), None) => Some(lib)
      case (None, Some(plugins)) => Some(plugins)
      case (Some(lib), Some(plugins)) => Some(lib ++ plugins)
    }
  }

  def crossBuildVersion(): Map[BinaryType, Version] = {
    binaries match {
      case None => Map()
      case Some(bins) => {
        bins
          .sortBy { b => Version(b.version) }
          .map { bin =>
            bin.name -> DependencyHelper.crossBuildVersion(bin.name, bin.version)
          }
          .toMap
      }
    }
  }

}

private[lib] object DependencyHelper {

  def crossBuildVersion(name: BinaryType, version: String): Version = {
    val versionObject = Version(version)
    name match {
      case BinaryType.Scala => {
        versionObject.tags.head match {
          case Tag.Semver(major, minor, _, _) => {
            // This is most common. We just want major and minor
            // version - e.g. 2.11.7 becomes 2.11.
            Version(s"${major}.${minor}", Seq(Tag.Semver(major, minor, 0)))
          }
          case _ => versionObject
        }
      }
      case BinaryType.Sbt => {
        // Get the binary-compatible version of sbt. Can be found by running `sbt sbtBinaryVersion`
        versionObject.tags
          .collectFirst {
            case Tag.Semver(1, _, _, _) => Version("1.0")
            case Tag.Semver(0, 13, _, _) => Version("0.13")
          }
          .getOrElse(versionObject)
      }
      case BinaryType.UNDEFINED(_) => {
        versionObject
      }
    }
  }
}

trait DependencyProvider {

  /** Returns the dependencies for this project.
    */
  def dependencies(project: Project)(implicit ec: ExecutionContext): Future[Dependencies]

}
