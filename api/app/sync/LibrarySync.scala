package sync

import db.{Authorization, InternalTask, InternalTasksDao, LibrariesDao, LibraryVersionsDao, ProjectLibrariesDao, ResolversDao, SyncsDao}
import io.flow.common.v0.models.UserReference
import io.flow.dependency.actors.SearchActor
import io.flow.dependency.api.lib.{ArtifactVersion, DefaultLibraryArtifactProvider}
import io.flow.dependency.v0.models.{Library, LibraryVersion, Resolver, VersionForm}
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

  private[this] val artifactProvider = DefaultLibraryArtifactProvider()

  private[this] def toVersionForm(version: ArtifactVersion): VersionForm = VersionForm(version.tag.value, version.crossBuildVersion.map(_.value))
  private[this] def toVersionForm(lv: LibraryVersion): VersionForm = VersionForm(lv.version, lv.crossBuildVersion)

  /**
   * Return the list of existing versions that we know about for this library
   */
  private[this] def existingVersions(libraryId: String): Set[VersionForm] = {
    libraryVersionsDao.findAll(
      Authorization.All,
      libraryId = Some(libraryId),
      limit = None,
    ).map(toVersionForm).toSet
  }

  /**
   * Return the list of all versions found on the resolver
   */
  private[this] def versionsOnResolver(lib: Library, resolver: Resolver): Seq[VersionForm] = {
    artifactProvider.resolve(
      resolversDao = resolversDao,
      resolver = resolver,
      groupId = lib.groupId,
      artifactId = lib.artifactId
    ) match {
      case None => Nil
      case Some(r) => r.versions.map(toVersionForm)
    }
  }

  def sync(user: UserReference, libraryId: String): Unit = {
    librariesDao.findById(Authorization.All, libraryId).foreach { lib =>
      lazy val existing = existingVersions(lib.id)

      syncsDao.withStartedAndCompleted("library", lib.id) {
        val newVersions: Seq[VersionForm] = resolversDao.findById(Authorization.All, lib.resolver.id).toSeq.flatMap { resolver =>
          versionsOnResolver(lib, resolver).filterNot(existing.contains)
        }
        newVersions.foreach { versionForm =>
          libraryVersionsDao.upsert(
            createdBy = user,
            libraryId = lib.id,
            form = versionForm,
          )
        }
        if (newVersions.nonEmpty) {
          createTaskToSyncProjectsDependentOnLibrary(lib.id)
        }
      }
    }
    searchActor ! SearchActor.Messages.SyncLibrary(libraryId)
  }

  def iterateAll(
    auth: Authorization,
    organizationId: Option[String] = None,
    prefix: Option[String] = None,
  )(f: Library => Any): Unit = {
    Pager.create { offset =>
      librariesDao.findAll(
        auth,
        organizationId = organizationId,
        prefix = prefix,
        offset = offset,
        limit = 1000,
      )
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
