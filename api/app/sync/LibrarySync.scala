package sync

import db.{Authorization, InternalTask, InternalTasksDao, LibrariesDao, LibraryVersionsDao, ProjectLibrariesDao, ResolversDao, SyncsDao}
import io.flow.common.v0.models.UserReference
import io.flow.dependency.actors.SearchActor
import io.flow.dependency.api.lib.{ArtifactVersion, DefaultLibraryArtifactProvider}
import io.flow.dependency.v0.models.{Library, LibraryVersion, VersionForm}
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

  private[this] def toVersionForm(version: ArtifactVersion): VersionForm = VersionForm(version.tag.value, version.crossBuildVersion.map(_.value))
  private[this] def toVersionForm(lv: LibraryVersion): VersionForm = VersionForm(lv.version, lv.crossBuildVersion)

  private[this] def existingVersions(libraryId: String): Set[VersionForm] = {
    libraryVersionsDao.findAll(
      Authorization.All,
      libraryId = Some(libraryId),
      limit = None,
    ).map(toVersionForm).toSet
  }

  def sync(user: UserReference, libraryId: String): Unit = {
    librariesDao.findById(Authorization.All, libraryId).foreach { lib =>
      lazy val existing = existingVersions(lib.id)
      var foundNewVersion = false

      syncsDao.withStartedAndCompleted("library", lib.id) {
        resolversDao.findById(Authorization.All, lib.resolver.id).map { resolver =>
          DefaultLibraryArtifactProvider().resolve(
            resolversDao = resolversDao,
            resolver = resolver,
            groupId = lib.groupId,
            artifactId = lib.artifactId
          ).map { resolution =>
            resolution.versions.map(toVersionForm).foreach { versionForm =>
              if (!existing.contains(versionForm)) {
                foundNewVersion = true
                libraryVersionsDao.upsert(
                  createdBy = user,
                  libraryId = lib.id,
                  form = versionForm,
                )
              }
            }
          }
        }
      }
      if (foundNewVersion) {
        createTaskToSyncProjectsDependentOnLibrary(lib.id)
      }

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

  private[this] def createTaskToSyncProjectsDependentOnLibrary(libraryId: String): Unit = {
    internalTasksDao.queueProjects(
      projectIds = projectLibrariesDao.findAll(
        Authorization.All,
        libraryId = Some(libraryId),
        limit = None,
      ).map(_.project.id),
      priority = InternalTask.MediumPriority
    )
  }
}
