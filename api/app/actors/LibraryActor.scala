package com.bryzek.dependency.actors

import com.bryzek.dependency.v0.models.{Library, LibraryForm, VersionForm}
import com.bryzek.dependency.api.lib.DefaultLibraryArtifactProvider
import io.flow.postgresql.Pager
import db.{Authorization, ItemsDao, LibrariesDao, LibraryVersionsDao, ProjectLibrariesDao, ResolversDao, SyncsDao, UsersDao}
import play.api.Logger
import akka.actor.Actor

object LibraryActor {

  object Messages {
    case class Data(id: String)
    case object Sync
    case object Deleted
  }

}

class LibraryActor extends Actor with Util {

  var dataLibrary: Option[Library] = None

  def receive = {

    case m @ LibraryActor.Messages.Data(id: String) => withErrorHandler(m) {
      dataLibrary = LibrariesDao.findById(Authorization.All, id)
    }

    case m @ LibraryActor.Messages.Sync => withErrorHandler(m) {
      dataLibrary.foreach { lib =>
        SyncsDao.withStartedAndCompleted(MainActor.SystemUser, "library", lib.id) {
          ResolversDao.findById(Authorization.All, lib.resolver.id).map { resolver =>
            DefaultLibraryArtifactProvider().resolve(
              resolver = resolver,
              groupId = lib.groupId,
              artifactId = lib.artifactId
            ).map { resolution =>
              println(s"Library[${lib.groupId}.${lib.artifactId}] resolver[${lib.resolver}] -- found[${resolution.resolver}]")

              resolution.versions.foreach { version =>
                LibraryVersionsDao.upsert(
                  createdBy = MainActor.SystemUser,
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
    }

    case m @ LibraryActor.Messages.Deleted => withErrorHandler(m) {
      dataLibrary.foreach { lib =>
        ItemsDao.deleteByObjectId(Authorization.All, MainActor.SystemUser, lib.id)

        Pager.create { offset =>
          ProjectLibrariesDao.findAll(Authorization.All, libraryId = Some(lib.id), limit = Some(100), offset = offset)
        }.foreach { projectLibrary =>
          ProjectLibrariesDao.removeLibrary(MainActor.SystemUser, projectLibrary)
          sender ! MainActor.Messages.ProjectLibrarySync(projectLibrary.project.id, projectLibrary.id)
        }
      }
      context.stop(self)
    }

    case m: Any => logUnhandledMessage(m)
  }

}
