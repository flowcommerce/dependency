package sync

import db._
import io.flow.dependency.actors.SearchActor
import io.flow.dependency.api.lib.{Dependencies, GithubDependencyProviderClient, GithubHelper, GithubUtil}
import io.flow.dependency.v0.models._
import io.flow.dependency.v0.models.json._
import io.flow.github.v0.models.HookForm
import io.flow.log.RollbarLogger
import io.flow.play.util.Config
import io.flow.postgresql.Pager
import javax.inject.Inject
import play.api.{Environment, Mode}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}

class ProjectSync @Inject()(
  env: Environment,
  config: Config,
  rollbar: RollbarLogger,
  projectsDao: ProjectsDao,
  projectBinariesDao:ProjectBinariesDao,
  projectLibrariesDao: ProjectLibrariesDao,
  recommendationsDao: RecommendationsDao,
  syncsDao: SyncsDao,
  tokensDao: TokensDao,
  staticUserProvider: StaticUserProvider,
  wsClient: WSClient,
  @javax.inject.Named("search-actor") searchActor: akka.actor.ActorRef,
) {
  private[this] val logger: RollbarLogger = rollbar.fingerprint(getClass.getName)
  private[this] val HookBaseUrl = config.requiredString("dependency.api.host") + "/webhooks/github/"
  private[this] val HookName = "web"
  private[this] val HookEvents = Seq(io.flow.github.v0.models.HookEvent.Push)

  def upserted(projectId: String)(implicit ec: ExecutionContext): Unit = {
    projectsDao.findById(Authorization.All, projectId) match {
      case Some(project) => {
        await(createHooks(project))
        ()
      }
      case None => {
        // TODO: Delete hooks
      }
    }
  }

  def sync(projectId: String)(implicit ec: ExecutionContext): Unit = {
    projectsDao.findById(Authorization.All, projectId).foreach { project =>
      syncsDao.withStartedAndCompleted("project", project.id) {
        await {
          doSync(project)
        }
      }
    }
    searchActor ! SearchActor.Messages.SyncProject(projectId)
  }

  def iterateAll()(f: Project => Any): Unit = {
    Pager.create { offset =>
      projectsDao.findAll(Authorization.All, offset = offset, limit = 1000)
    }.foreach { rec =>
      f(rec)
    }
  }

  private[this] def doSync(project: Project)(implicit ec: ExecutionContext): Future[Unit] = {
    syncsDao.recordStarted("project", project.id)

    val summary = projectsDao.toSummary(project)

    GithubDependencyProviderClient.instance(wsClient, config, tokensDao, summary, project.user, logger).dependencies(project).map { dependencies =>
      syncBinaries(project, dependencies.binaries.getOrElse(Nil))
      syncArtifacts(project, dependencies)
      recommendationsDao.sync(staticUserProvider.systemUser, project)
    }
  }

  private[this] def syncBinaries(project: Project, binaries: Seq[ProjectBinaryForm]): Unit = {
    val projectBinaries = binaries.map { form =>
      projectBinariesDao.upsert(project.user, form) match {
        case Left(errors) => {
          logger
            .withKeyValue("project", Json.toJson(project))
            .withKeyValue("form", form.toString)
            .withKeyValues("error", errors)
            .warn("Errors upserting binary")
          None
        }
        case Right(projectBinary) => {
          Some(projectBinary)
        }
      }
    }
    projectBinariesDao.setIds(project.user, project.id, projectBinaries.flatten)
  }

  private[this] def syncArtifacts(project: Project, dependencies: Dependencies): Unit = {
    val projectLibraries = dependencies.librariesAndPlugins.getOrElse(Nil).map { artifact =>
      val bins = dependencies.crossBuildVersion()
      val crossBuildVersion = {
        if (artifact.isPlugin)
          bins.get(BinaryType.Sbt).orElse(bins.get(BinaryType.Scala))
        else
          bins.get(BinaryType.Scala)
      }
      projectLibrariesDao.upsert(
        project.user,
        artifact.toProjectLibraryForm(
          crossBuildVersion = crossBuildVersion
        )
      ) match {
        case Left(errors) => {
          logger
            .withKeyValue("project", Json.toJson(project))
            .withKeyValues("error", errors)
            .withKeyValue("artifact", artifact.toString)
            .warn("Validation errors storing artifact")
          None
        }
        case Right(library) => {
          Some(library)
        }
      }
    }
    projectLibrariesDao.setIds(project.user, project.id, projectLibraries.flatten)
  }

  private[this] def createHooks(project: Project)(implicit ec: ExecutionContext): Future[Either[Unit, Unit]] = {
    GithubUtil.parseUri(project.uri) match {
      case Left(error) => {
        logger.withKeyValue("project", Json.toJson(project)).withKeyValue("error", error).warn("error creating github hooks")
        Future.successful(Left(()))
      }
      case Right(repo) => {
        tokensDao.getCleartextGithubOauthTokenByUserId(project.user.id) match {
          case None => {
            if (env.mode != Mode.Test) {
              logger
                .withKeyValue("project_id", project.id)
                .withKeyValue("user_id", project.user.id)
                .warn("No oauth token for user")
            }
            Future.successful(Left(()))
          }

          case Some(token) => {
            val client = GithubHelper.apiClient(wsClient, token)

            client.hooks.get(repo.owner, repo.project).flatMap { hooks =>
              val targetUrl = HookBaseUrl + project.id
              if (hooks.exists(_.config.url.contains(targetUrl))) {
                Future.successful(Right(()))
              } else {
                client.hooks.post(
                  owner = repo.owner,
                  repo = repo.project,
                  hookForm = HookForm(
                    name = HookName,
                    config = io.flow.github.v0.models.HookConfig(
                      url = Some(targetUrl),
                      contentType = Some("json")
                    ),
                    events = HookEvents,
                    active = true
                  )
                ).map { _ =>
                  Right(())
                }
              }
            }
          }
        }
      }
    }
  }

  private[this] def await[T](f: => Future[T])(implicit ec: ExecutionContext): Either[Unit, T] = {
    Await.result(
      f.map { r => Right(r) }
        .recover {
        case e => {
          logger.error("Error waiting for future", e)
          Left(())
        }
      },
      FiniteDuration(30, SECONDS)
    )
  }
}
