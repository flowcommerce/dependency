package io.flow.dependency.api.lib

import db.{ProjectBinaryForm, TokensDao}
import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.models.{BinaryType, Project, ProjectSummary}
import io.flow.github.v0.models.{Contents, Encoding}
import io.flow.util.Config
import org.apache.commons.codec.binary.Base64

import scala.concurrent.{ExecutionContext, Future}

import io.flow.log.RollbarLogger
import play.api.libs.ws.WSClient

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
          case _ :: Nil => Left(s"Invalid uri path[$uri] missing project name")
          case owner :: project :: Nil => Right(Repository(owner, project))
          case _ => Left(s"Invalid uri path[$u] - expected exactly two path components")
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

  def instance(wsClient: WSClient,
    config: Config,
    tokensDao: TokensDao,
    project: ProjectSummary,
    user: UserReference,
    logger: RollbarLogger
  ) = {
    new GithubDependencyProvider(new DefaultGithub(wsClient, config, tokensDao), project, user, logger)
  }

}

private[lib] case class GithubDependencyProvider(
  github: Github,
  project: ProjectSummary,
  user: UserReference,
  logger: RollbarLogger
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
          contents = text,
          logger = logger
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
          contents = text,
          logger = logger
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
          path = ProjectPluginsSbtFilename,
          logger = logger
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
