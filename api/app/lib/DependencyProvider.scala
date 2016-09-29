package com.bryzek.dependency.api.lib

import db.ProjectBinaryForm
import com.bryzek.dependency.v0.models.{LibraryForm, BinaryType, Project}
import play.api.Logger
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

  def crossBuildVersion(): Option[Version] = {
    binaries match {
      case None => None
      case Some(langs) => {
        langs.sortBy { l => Version(l.version) }.reverse.find(_.name == BinaryType.Scala).headOption.map { lang =>
          DependencyHelper.crossBuildVersion(lang.name, lang.version)
        }
      }
    }
  }

}

private[lib] object DependencyHelper {

  def crossBuildVersion(name: BinaryType, version: String): Version = {
    val versionObject = Version(version)
    name match {
      case BinaryType.Scala |  BinaryType.Sbt=> {
        versionObject.tags.head match {
          case Tag.Semver(major, minor, _, _) => {
            // This is most common. We just want major and minor
            // version - e.g. 2.11.7 becomes 2.11.
            Version(s"${major}.${minor}", Seq(Tag.Semver(major, minor, 0)))
          }
          case _ => versionObject
        }
      }
      case BinaryType.UNDEFINED(_) => {
        versionObject
      }
    }
  }
}


trait DependencyProvider {

  /**
    * Returns the dependencies for this project.
    */
  def dependencies(project: Project)(implicit ec: ExecutionContext): Future[Dependencies]

}
