package io.flow.dependency.api.lib

import io.flow.dependency.v0.models.Credentials
import io.flow.util.Version
import org.apache.commons.lang3.StringUtils

object RemoteVersions {

  def fetch(
    resolver: String,
    groupId: String,
    artifactId: String,
    credentials: Option[Credentials]
  ): Seq[ArtifactVersion] = {
    val versions = fetchUrl(
      joinUrl(resolver, groupId.replaceAll("\\.", "/")),
      artifactId,
      credentials
    ) match {
      case Nil => {
        fetchUrl(joinUrl(resolver, groupId), artifactId, credentials)
      }
      case results => {
        results
      }
    }
    versions.sortBy { _.tag }.reverse
  }

  private[this] def fetchUrl(
    url: String,
    artifactId: String,
    credentials: Option[Credentials]
  ): Seq[ArtifactVersion] = {
    val result = RemoteDirectory.fetch(url, credentials = credentials)(
      filter = { name => name == artifactId || name.startsWith(artifactId + "_") }
    )

    result.directories.flatMap { dir =>
      val thisUrl = joinUrl(url, dir)
      RemoteDirectory.fetch(thisUrl, credentials = credentials)().directories.map { d =>
        ArtifactVersion(
          tag = Version(StringUtils.stripEnd(d, "/")),
          crossBuildVersion = crossBuildVersion(dir)
        )
      }
    }
  }

  // e.g. "scala-csv_2.11/" => 2.11
  def crossBuildVersion(text: String): Option[Version] = {
    StringUtils.stripEnd(text, "/").split("_").toList match {
      case Nil => None
      case one :: Nil => None
      case inple => {
        // Check if we can successfully parse the version tag for a
        // major version. If so, we assume we have found a cross build
        // version.
        val tag = Version(inple.last)
        tag.major match {
          case None => None
          case Some(_) => Some(tag)
        }
      }
    }
  }

  def makeUrls(
    resolver: String,
    groupId: String
  ): Seq[String] = {
    Seq(
      joinUrl(
        resolver, groupId.replaceAll("\\.", "/")
      ),
      joinUrl(resolver, groupId)
    )
  }

  def joinUrl(
    a: String,
    b: String
  ): String = {
    Seq(a, b).map ( StringUtils.stripEnd(_, "/") ).mkString("/")
  }
}
