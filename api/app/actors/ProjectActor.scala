package io.flow.dependency.actors

import io.flow.dependency.api.lib._
import io.flow.dependency.v0.models._
import io.flow.dependency.v0.models.json._
import io.flow.postgresql.Pager
import io.flow.play.util.Config
import db._
import akka.actor.{Actor, ActorSystem}
import io.flow.akka.SafeReceive
import io.flow.github.v0.models.HookForm
import io.flow.log.RollbarLogger
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

object ProjectActor {

  trait Message

  object Messages {

    case object Deleted extends Message
    case object CreateHooks extends Message
    case object Sync extends Message
    case object SyncCompleted extends Message

    case class ProjectLibraryCreated(id: String) extends Message
    case class ProjectLibrarySync(id: String) extends Message
    case class ProjectLibraryDeleted(id: String, version: String) extends Message

    case class ProjectBinaryCreated(id: String) extends Message
    case class ProjectBinarySync(id: String) extends Message
    case class ProjectBinaryDeleted(id: String, version: String) extends Message

    case class LibrarySynced(id: String) extends Message
    case class BinarySynced(id: String) extends Message
  }

  trait Factory {
    def apply(projectId: String): Actor
  }
}

class ProjectActor @javax.inject.Inject() (
  logger: RollbarLogger,
  config: Config,
  actorSystem: ActorSystem,
  projectsDao: ProjectsDao,
  tokensDao: TokensDao,
  syncsDao: SyncsDao,
  projectBinariesDao:ProjectBinariesDao,
  projectLibrariesDao: ProjectLibrariesDao,
  recommendationsDao: RecommendationsDao,
  librariesDao: LibrariesDao,
  binariesDao: BinariesDao,
  usersDao: UsersDao,
  resolversDao: ResolversDao,
  wsClient: WSClient,
  @com.google.inject.assistedinject.Assisted projectId: String
) extends Actor {

  private[this] lazy val SystemUser = usersDao.systemUser

  private[this] implicit val ec : ExecutionContext = actorSystem.dispatchers.lookup("project-actor-context")
  private[this] implicit val configuredRollbar: RollbarLogger = logger.fingerprint(getClass.getName)

  private[this] val HookBaseUrl = config.requiredString("dependency.api.host") + "/webhooks/github/"
  private[this] val HookName = "web"
  private[this] val HookEvents = Seq(io.flow.github.v0.models.HookEvent.Push)

  private[this] lazy val dataProject: Option[Project] = projectsDao.findById(Authorization.All, projectId)

  def receive: Receive = SafeReceive.withLogUnhandled {

    case ProjectActor.Messages.ProjectLibraryCreated(id) =>
      syncProjectLibrary(id)

    case ProjectActor.Messages.ProjectLibrarySync(id) =>
      syncProjectLibrary(id)

    case ProjectActor.Messages.ProjectBinaryCreated(id) =>
      syncProjectBinary(id)

    case ProjectActor.Messages.ProjectBinarySync(id) =>
      syncProjectBinary(id)

    case ProjectActor.Messages.LibrarySynced(_) =>
      dataProject.foreach { project =>
        processPendingSync(project)
      }

    case ProjectActor.Messages.BinarySynced(_) =>
      dataProject.foreach { project =>
        processPendingSync(project)
      }

    case ProjectActor.Messages.CreateHooks =>
      dataProject.foreach { project =>
        GithubUtil.parseUri(project.uri) match {
          case Left(error) => {
            logger.fingerprint(getClass.getName).withKeyValue("project", Json.toJson(project)).withKeyValue("error", error).warn("error creating github hooks")
          }
          case Right(repo) => {
            tokensDao.getCleartextGithubOauthTokenByUserId(project.user.id) match {
              case None => {
                logger.fingerprint(getClass.getName).withKeyValue("project", Json.toJson(project)).warn(s"No oauth token for user")
              }

              case Some(token) => {
                val client = GithubHelper.apiClient(wsClient, token)

                client.hooks.get(repo.owner, repo.project).map { hooks =>
                  val targetUrl = HookBaseUrl + project.id
                  hooks.find(_.config.url.contains(targetUrl)).getOrElse {
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
                      ()
                    }.recover {
                      case e: Throwable => {
                        logger.fingerprint(getClass.getName).withKeyValue("project", Json.toJson(project)).error("Error creating hook", e)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

    case ProjectActor.Messages.Sync =>
      dataProject.foreach { project =>
        syncsDao.recordStarted("project", project.id)

        val summary = projectsDao.toSummary(project)

        GithubDependencyProviderClient.instance(wsClient, config, tokensDao, summary, project.user, logger).dependencies(project).map { dependencies =>
          dependencies.binaries.map { binaries =>
            val projectBinaries = binaries.map { form =>
              projectBinariesDao.upsert(project.user, form) match {
                case Left(errors) => {
                  logger.withKeyValue("project", Json.toJson(project)).withKeyValue("form", form.toString).withKeyValue("errors", errors).error(s"Errors storing binary")
                  None
                }
                case Right(projectBinary) => {
                  Some(projectBinary)
                }
              }
            }
            projectBinariesDao.setIds(project.user, project.id, projectBinaries.flatten)
          }

          dependencies.librariesAndPlugins.map { libraries =>
            val projectLibraries = libraries.map { artifact =>
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
                  logger.withKeyValue("project", Json.toJson(project)).withKeyValue("errors", errors).withKeyValue("artifact", artifact.toString).error(s"Error storing artifact")
                  None
                }
                case Right(library) => {
                  Some(library)
                }
              }
            }
            projectLibrariesDao.setIds(project.user, project.id, projectLibraries.flatten)
          }

          recommendationsDao.sync(SystemUser, project)

          processPendingSync(project)
        }.recover {
          case e => {
            logger.withKeyValue("project", Json.toJson(project)).error(s"Error fetching dependencies", e)
          }
        }
      }

    case ProjectActor.Messages.Deleted =>
      dataProject.foreach { project =>
        Pager.create { offset =>
          recommendationsDao.findAll(Authorization.All, projectId = Some(project.id), offset = offset)
        }.foreach { rec =>
          recommendationsDao.delete(SystemUser, rec)
        }
      }
      context.stop(self)

    case ProjectActor.Messages.ProjectLibraryDeleted(id, version) =>
      dataProject.foreach { project =>
        recommendationsDao.findAll(
          Authorization.All,
          projectId = Some(project.id),
          `type` = Some(RecommendationType.Library),
          objectId = Some(id),
          fromVersion = Some(version)
        ).foreach { rec =>
          recommendationsDao.delete(SystemUser, rec)
        }

        processPendingSync(project)
      }

    case ProjectActor.Messages.ProjectBinaryDeleted(id, version) =>
      dataProject.foreach { project =>
        recommendationsDao.findAll(
          Authorization.All,
          projectId = Some(project.id),
          `type` = Some(RecommendationType.Binary),
          objectId = Some(id),
          fromVersion = Some(version)
        ).foreach { rec =>
          recommendationsDao.delete(SystemUser, rec)
        }
        processPendingSync(project)
      }
  }

  /**
    * Attempts to resolve the library. If successful, sets the
    * project_libraries.library_id
    */
  def syncProjectLibrary(id: String): Unit = {
    syncsDao.withStartedAndCompleted("project_library", id) {
      dataProject.foreach { project =>
        projectLibrariesDao.findById(Authorization.All, id).map { projectLibrary =>
          resolveLibrary(projectLibrary).map { lib =>
            projectLibrariesDao.setLibrary(SystemUser, projectLibrary, lib)
          }
        }
        processPendingSync(project)
      }
    }
  }

  def syncProjectBinary(id: String): Unit = {
    syncsDao.withStartedAndCompleted("project_binary", id) {
      dataProject.foreach { project =>
        projectBinariesDao.findById(Authorization.All, id).map { projectBinary =>
          resolveBinary(projectBinary).map { binary =>
            projectBinariesDao.setBinary(SystemUser, projectBinary, binary)
          }
        }
        processPendingSync(project)
      }
    }
  }

  def processPendingSync(project: Project): Unit = {
    dependenciesPendingCompletion(project) match {
      case Nil => {
        recommendationsDao.sync(SystemUser, project)
        syncsDao.recordCompleted("project", project.id)
      }
      case _ => {
        // println(s" -- project[${project.name}] id[${project.id}] waiting on dependencies to sync: " + deps.mkString(", "))
      }
    }
  }

  // NB: We don't return ALL dependencies
  private[this] def dependenciesPendingCompletion(project: Project): Seq[String] = {
    projectLibrariesDao.findAll(
      Authorization.All,
      projectId = Some(project.id),
      isSynced = Some(false),
      limit = None
    ).map( lib => s"Library ${lib.groupId}.${lib.artifactId}" ) ++
    projectBinariesDao.findAll(
      Authorization.All,
      projectId = Some(project.id),
      isSynced = Some(false)
    ).map( bin => s"Binary ${bin.name}" )
  }

  private[this] def resolveLibrary(projectLibrary: ProjectLibrary): Option[Library] = {
    librariesDao.findByGroupIdAndArtifactId(Authorization.All, projectLibrary.groupId, projectLibrary.artifactId) match {
      case Some(lib) => {
        Some(lib)
      }
      case None => {
        DefaultLibraryArtifactProvider().resolve(
          resolversDao = resolversDao,
          organization = projectLibrary.project.organization,
          groupId = projectLibrary.groupId,
          artifactId = projectLibrary.artifactId
        ) match {
          case None => {
            None
          }
          case Some(resolution) => {
            librariesDao.upsert(
              SystemUser,
              form = LibraryForm(
                organizationId = projectLibrary.project.organization.id,
                groupId = projectLibrary.groupId,
                artifactId = projectLibrary.artifactId,
                resolverId = resolution.resolver.id
              )
            ) match {
              case Left(errors) => {
                logger.withKeyValue("project", Json.toJson(projectLibrary)).withKeyValue("errors", errors).error(s"Error upserting library")
                None
              }
              case Right(library) => {
                Some(library)
              }
            }
          }
        }
      }
    }
  }

  private[this] def resolveBinary(projectBinary: ProjectBinary): Option[Binary] = {
    BinaryType(projectBinary.name) match {
      case BinaryType.Scala | BinaryType.Sbt => {
        binariesDao.upsert(
          SystemUser,
          BinaryForm(
            organizationId = projectBinary.project.organization.id,
            name = BinaryType(projectBinary.name)
          )
        ) match {
          case Left(errors) => {
            logger.withKeyValue("project", Json.toJson(projectBinary)).withKeyValue("errors", errors).error(s"error upserting binary")
            None
          }
          case Right(binary) => {
            Some(binary)
          }
        }
      }
      case BinaryType.UNDEFINED(_) => {
        logger.withKeyValue("project", Json.toJson(projectBinary)).warn(s"Project references an unknown binary")
        None
      }
    }
  }

}
