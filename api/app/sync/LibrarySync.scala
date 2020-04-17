package sync

import db.{Authorization, InternalTask, InternalTasksDao, LibrariesDao, LibraryVersionsDao, ProjectLibrariesDao, ResolversDao, SyncsDao}
import io.flow.common.v0.models.UserReference
import io.flow.dependency.actors.SearchActor
import io.flow.dependency.api.lib.DefaultLibraryArtifactProvider
import io.flow.dependency.v0.models.{Library, VersionForm}
import io.flow.postgresql.Pager
import javax.inject.Inject

class LibrarySync @Inject()(
  librariesDao: LibrariesDao,
  resolversDao: ResolversDao,
  libraryVersionsDao: LibraryVersionsDao,
  projectLibrariesDao: ProjectLibrariesDao,
  internalTasksDao: InternalTasksDao,
  syncsDao: SyncsDao,
  @javax.inject.Named("search-actor") searchActor: akka.actor.ActorRef,
) {

  def sync(user: UserReference, libraryId: String): Unit = {
    librariesDao.findById(Authorization.All, libraryId).foreach { lib =>
      syncsDao.withStartedAndCompleted("library", lib.id) {
        resolversDao.findById(Authorization.All, lib.resolver.id).map { resolver =>
          DefaultLibraryArtifactProvider().resolve(
            resolversDao = resolversDao,
            resolver = resolver,
            groupId = lib.groupId,
            artifactId = lib.artifactId
          ).map { resolution =>
            resolution.versions.foreach { version =>
              libraryVersionsDao.upsert(
                createdBy = user,
                libraryId = lib.id,
                form = VersionForm(version.tag.value, version.crossBuildVersion.map(_.value))
              )
            }
          }
        }
      }
      createTaskToSyncProjectsDependentOnLibrary(lib.id)
    }
    searchActor ! SearchActor.Messages.SyncLibrary(libraryId)
  }

  def iterateAll(organizationId: Option[String] = None)(f: Library => Any): Unit = {
    Pager.create { offset =>
      librariesDao.findAll(Authorization.All, organizationId = organizationId, offset = offset, limit = 1000)
    }.foreach { rec =>
      f(rec)
    }
  }

  def createTaskToSyncProjectsDependentOnLibrary(libraryId: String): Unit = {
    val projectIds = projectLibrariesDao.findAll(
      Authorization.All,
      libraryId = Some(libraryId),
      limit = None,
    ).map(_.project.id).distinct
    if (projectIds.nonEmpty) {
      internalTasksDao.queueProjects(projectIds, priority = InternalTask.MediumPriority)
    }
  }
}
