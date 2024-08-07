package io.flow.dependency.api.lib

import io.flow.dependency.v0.models.VersionForm
import io.flow.util.{Tag, Version}

object Recommendations {

  /** Given the current version and a list of possible versions, suggests the best version to which to upgrade (or None
    * if not found)
    */
  def version(current: VersionForm, others: Seq[VersionForm], allowMajorVersionUpgrade: Boolean): Option[String] = {
    val currentTag = Version(current.version) // TODO: are we missing crossBuildVersion here?

    others
      .filter(_ != current)
      .filter(v => matchesCrossBuild(v.crossBuildVersion, current.crossBuildVersion))
      .map(v => Version(v.version))
      .filter(_ > currentTag)
      .filter(v => allowMajorVersionUpgrade || currentTag.major == v.major)
      .filter(v => isSimple(v) || (currentTag.tags.size == v.tags.size && matchesText(currentTag, v)))
      .sorted
      .reverse
      .headOption
      .map { _.value }
  }

  private[this] def matchesText(current: Version, other: Version): Boolean = {
    (current.tags zip other.tags)
      .map { case (a, b) =>
        (a, b) match {
          case (_: Tag.Semver, _: Tag.Semver) => true
          case (_: Tag.Date, _: Tag.Date) => true
          case (Tag.Text(value1), Tag.Text(value2)) => value1 == value2
          case (_, _) => false
        }
      }
      .forall(el => el)
  }

  private[this] def isSimple(version: Version): Boolean = {
    version.tags match {
      case Seq(_: Tag.Semver) => true
      case Seq(_: Tag.Date) => true
      case _ => false
    }
  }

  private[this] def matchesCrossBuild(current: Option[String], other: Option[String]): Boolean = {
    (current, other) match {
      case (None, None) => true
      case (None, Some(_)) => false
      case (Some(_), None) => false
      case (Some(a), Some(b)) => {
        (a == b) match {
          case true => true
          case false => {
            // In this case, we fuzzy match. Main use case was from
            // scala - some libraries cross built for 2.11 need to
            // match 2.11.7
            (Version(a).tags.head, Version(b).tags.head) match {
              case (a: Tag.Semver, b: Tag.Semver) => {
                a.major == b.major && a.minor == b.minor
              }
              case _ => false
            }
          }
        }
      }
    }
  }
}
