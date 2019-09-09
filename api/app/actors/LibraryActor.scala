package io.flow.dependency.actors

import javax.inject.Inject
import io.flow.dependency.v0.models.{Library, VersionForm}
import io.flow.dependency.api.lib.DefaultLibraryArtifactProvider
import io.flow.postgresql.Pager
import db.{Authorization, ItemsDao, LibrariesDao, LibraryVersionsDao, ProjectLibrariesDao, ResolversDao, SyncsDao, UsersDao}
import akka.actor.Actor
import io.flow.akka.SafeReceive
import io.flow.log.RollbarLogger

object LibraryActor {

  object Messages {
    case class Data(id: String)
    case object Sync
    case object Deleted
  }

}

class LibraryActor @Inject()
(
  librariesDao: LibrariesDao,
  syncsDao: SyncsDao,
  resolversDao: ResolversDao,
  libraryVersionsDao: LibraryVersionsDao,
  itemsDao: ItemsDao,
  projectLibrariesDao: ProjectLibrariesDao,
  usersDao: UsersDao,
  logger: RollbarLogger
) extends Actor {

  var dataLibrary: Option[Library] = None
  lazy val SystemUser = usersDao.systemUser
  private[this] implicit val configuredRollbar = logger.fingerprint("LibraryActor")

  def receive = SafeReceive.withLogUnhandled {

    case LibraryActor.Messages.Data(id: String) => 
      dataLibrary = librariesDao.findById(Authorization.All, id)

    case LibraryActor.Messages.Sync => 
      dataLibrary.foreach { lib =>
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
                  createdBy = SystemUser,
                  libraryId = lib.id,
                  form = VersionForm(version.tag.value, version.crossBuildVersion.map(_.value))
                )
              }
            }
          }
        }

        // TODO: Should we only send if something changed?
        sender ! MainActor.Messages.LibrarySyncCompleted(lib.id)
      }

    case LibraryActor.Messages.Deleted => 
      dataLibrary.foreach { lib =>
        itemsDao.deleteByObjectId(Authorization.All, SystemUser, lib.id)

        Pager.create { offset =>
          projectLibrariesDao.findAll(Authorization.All, libraryId = Some(lib.id), limit = Some(100), offset = offset)
        }.foreach { projectLibrary =>
          projectLibrariesDao.removeLibrary(SystemUser, projectLibrary)
          sender ! MainActor.Messages.ProjectLibrarySync(projectLibrary.project.id, projectLibrary.id)
        }
      }
      context.stop(self)
  }

}
