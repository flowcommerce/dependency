package io.flow.dependency.actors

import cache.ProjectsCache
import db._
import io.flow.akka.SafeReceive
import io.flow.akka.actor.ReapedActor
import io.flow.dependency.api.lib._
import io.flow.dependency.v0.models._
import io.flow.dependency.v0.models.json._
import io.flow.log.RollbarLogger
import io.flow.postgresql.{OrderBy, Pager}
import play.api.libs.json.Json

object ProjectActor {

  trait Message

  object Messages {

    case class Delete(projectId: String) extends Message
    case class SyncCompleted(projectId: String) extends Message

    case class ProjectLibraryCreated(projectId: String, id: String) extends Message
    case class ProjectLibrarySync(projectId: String, id: String) extends Message
    case class ProjectLibraryDeleted(projectId: String, id: String, version: String) extends Message

    case class ProjectBinaryCreated(projectId: String, id: String) extends Message
    case class ProjectBinarySync(projectId: String, id: String) extends Message
    case class ProjectBinaryDeleted(projectId: String, id: String, version: String) extends Message

    case class LibrarySynced(projectId: String) extends Message
    case class BinarySynced(projectId: String) extends Message
  }
}

class ProjectActor @javax.inject.Inject() (
  rollbar: RollbarLogger,
  projectsCache: ProjectsCache,
  syncsDao: SyncsDao,
  projectBinariesDao:ProjectBinariesDao,
  projectLibrariesDao: InternalProjectLibrariesDao,
  recommendationsDao: RecommendationsDao,
  librariesDao: LibrariesDao,
  binariesDao: BinariesDao,
  staticUserProvider: StaticUserProvider,
  defaultLibraryArtifactProvider: DefaultLibraryArtifactProvider,
) extends ReapedActor {

  private[this] lazy val SystemUser = staticUserProvider.systemUser

  private[this] implicit val logger: RollbarLogger = rollbar.fingerprint(getClass.getName)

  def receive: Receive = SafeReceive.withLogUnhandled {

    case ProjectActor.Messages.ProjectLibraryCreated(projectId, id) =>
      syncProjectLibrary(projectId, id)

    case ProjectActor.Messages.ProjectLibrarySync(projectId, id) =>
      syncProjectLibrary(projectId, id)

    case ProjectActor.Messages.ProjectBinaryCreated(projectId, id) =>
      syncProjectBinary(projectId, id)

    case ProjectActor.Messages.ProjectBinarySync(projectId, id) =>
      syncProjectBinary(projectId, id)

    case ProjectActor.Messages.LibrarySynced(projectId) =>
      findProject(projectId).foreach { project =>
        processPendingSync(project)
      }

    case ProjectActor.Messages.BinarySynced(projectId) =>
      findProject(projectId).foreach { project =>
        processPendingSync(project)
      }

    case ProjectActor.Messages.Delete(projectId: String) =>
      Pager.create { offset =>
        recommendationsDao.findAll(Authorization.All, projectId = Some(projectId), offset = offset)
      }.foreach { rec =>
        recommendationsDao.delete(SystemUser, rec)
      }

    case ProjectActor.Messages.ProjectLibraryDeleted(projectId, id, version) =>
      findProject(projectId).foreach { project =>
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

    case ProjectActor.Messages.ProjectBinaryDeleted(projectId, id, version) =>
      findProject(projectId).foreach { project =>
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
  def syncProjectLibrary(projectId: String, id: String): Unit = {
    syncsDao.withStartedAndCompleted("project_library", id) {
      findProject(projectId).foreach { project =>
        projectLibrariesDao.findById(Authorization.All, id).map { projectLibrary =>
          resolveLibrary(projectLibrary).map { lib =>
            projectLibrariesDao.setLibrary(SystemUser, projectLibrary, lib)
          }
        }
        processPendingSync(project)
      }
    }
  }

  def syncProjectBinary(projectId: String, id: String): Unit = {
    syncsDao.withStartedAndCompleted("project_binary", id) {
      findProject(projectId).foreach { project =>
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
      limit = None,
      orderBy = Some(OrderBy("group_id,artifact_id")),
    ).map( lib => s"Library ${lib.groupId}.${lib.artifactId}" ) ++
    projectBinariesDao.findAll(
      Authorization.All,
      projectId = Some(project.id),
      isSynced = Some(false)
    ).map( bin => s"Binary ${bin.name}" )
  }

  private[this] def resolveLibrary(projectLibrary: InternalProjectLibrary): Option[Library] = {
    librariesDao.findByGroupIdAndArtifactId(Authorization.All, projectLibrary.groupId, projectLibrary.artifactId) match {
      case Some(lib) => {
        Some(lib)
      }
      case None => {
        defaultLibraryArtifactProvider.resolve(
          organizationId = projectLibrary.organizationId,
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
                organizationId = projectLibrary.organizationId,
                groupId = projectLibrary.groupId,
                artifactId = projectLibrary.artifactId,
                resolverId = resolution.resolver.id
              )
            ) match {
              case Left(errors) => {
                logger
                  .organization(projectLibrary.organizationId)
                  .withKeyValue("project_id", projectLibrary.projectId)
                  .withKeyValue("errors", errors).error("Error upserting library")
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

  private[this] def findProject(id: String): Option[Project] = {
    projectsCache.findByProjectId(id)
  }
}
