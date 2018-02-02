package io.flow.dependency.api.lib

import db.ProjectBinaryForm
import io.flow.common.v0.models.{User, UserReference}
import io.flow.dependency.v0.models.{BinaryForm, BinaryType, LibraryForm, Project, ProjectSummary}
import io.flow.github.v0.Client
import io.flow.github.v0.errors.UnitResponse
import io.flow.github.v0.models.{Contents, Encoding}
import io.flow.play.util.DefaultConfig
import org.apache.commons.codec.binary.Base64

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import java.net.URI

object GithubUtil {

  case class Repository(
    owner: String,
    project: String
  )

  def parseUri(uri: String): Either[String, Repository] = {
    Validation.validateUri(uri) match {
      case Left(errors) => Left(errors.mkString(", "))
      case Right(u) => {
        val path = if (u.getPath.startsWith("/")) {
          u.getPath.substring(1)
        } else {
          u.getPath
        }.trim
        path.split("/").filter(!_.isEmpty).toList match {
          case Nil => Left(s"URI path cannot be empty for uri[$uri]")
          case owner :: Nil => Left(s"Invalid uri path[$uri] missing project name")
          case owner :: project :: Nil => Right(Repository(owner, project))
          case inple => Left(s"Invalid uri path[$u] - expected exactly two path components")
        }
      }
    }
  }

  def toText(contents: Contents): String = {
    (contents.content, contents.encoding) match {
      case (Some(encoded), Encoding.Base64) => {
        new String(Base64.decodeBase64(encoded.getBytes))
      }
      case (Some(contents), Encoding.Utf8) => {
        contents
      }
      case (Some(_), Encoding.UNDEFINED(name)) => {
        sys.error(s"Unsupported encoding[$name] for content: $contents")
      }
      case (None, _) => {
        sys.error(s"No contents for: $contents")
      }
    }
  }

}

object GithubDependencyProviderClient {

  def instance(project: ProjectSummary, user: UserReference) = {
    new GithubDependencyProvider(new DefaultGithub(), project, user)
  }

}

private[lib] case class GithubDependencyProvider(
  github: Github,
  project: ProjectSummary,
  user: UserReference
) extends DependencyProvider {

  private val BuildSbtFilename = "build.sbt"
  private val ProjectPluginsSbtFilename = "project/plugins.sbt"
  private val BuildPropertiesFilename = "project/build.properties"

  override def dependencies(project: Project)(implicit ec: ExecutionContext): Future[Dependencies] = {
    for {
      build <- getBuildDependencies(project.uri)
      plugins <- getPluginsDependencies(project.uri)
      properties <- parseProperties(project.uri)
    } yield {
      Seq(build, plugins, properties).flatten.foldLeft(Dependencies()) { case (all, dep) =>
        all.copy(
          libraries = Some((all.libraries.getOrElse(Nil) ++ dep.libraries.getOrElse(Nil)).distinct),
          resolverUris = Some((all.resolverUris.getOrElse(Nil) ++ dep.resolverUris.getOrElse(Nil)).distinct),
          plugins = Some((all.plugins.getOrElse(Nil) ++ dep.plugins.getOrElse(Nil)).distinct),
          binaries = Some((all.binaries.getOrElse(Nil) ++ dep.binaries.getOrElse(Nil)).distinct)
        )
      }
    }
  }

  private[this] def getBuildDependencies(
    projectUri: String
  ) (
    implicit ec: ExecutionContext
  ): Future[Option[Dependencies]] = {
    github.file(user, projectUri, BuildSbtFilename).map { result =>
      result.flatMap { text =>
        val result = BuildSbtScalaParser(
          project = project,
          path = BuildSbtFilename,
          contents = text
        )
        Some(
          Dependencies(
            binaries = Some(result.binaries),
            libraries = Some(result.libraries),
            resolverUris = Some(result.resolverUris)
          )
        )
      }
    }
  }

  private[this] def parseProperties(
    projectUri: String
  ) (
    implicit ec: ExecutionContext
  ): Future[Option[Dependencies]] = {
    github.file(user, projectUri, BuildPropertiesFilename).map { result =>
      result.flatMap { text =>
        val properties = PropertiesParser(
          project = project,
          path = BuildPropertiesFilename,
          contents = text
        )
        properties.get("sbt.version").map { value =>
          Dependencies(
            Some(
              Seq(
                ProjectBinaryForm(
                  projectId = project.id,
                  name = BinaryType.Sbt,
                  version = value,
                  path = BuildPropertiesFilename
                )
              )
            )
          )
        }
      }
    }
  }

  private[this] def getPluginsDependencies(
    projectUri: String
  ) (
    implicit ec: ExecutionContext
  ): Future[Option[Dependencies]] = {
    github.file(user, projectUri, ProjectPluginsSbtFilename).map { result =>
      result.flatMap { text =>
        val result = ProjectPluginsSbtScalaParser(
          project = project,
          contents = text,
          path = ProjectPluginsSbtFilename
        )
        Some(
          Dependencies(
            plugins = Some(result.plugins),
            resolverUris = Some(result.resolverUris)
          )
        )
      }
    }
  }

}
