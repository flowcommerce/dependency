package db

import javax.inject.{Inject, Singleton}
import io.flow.dependency.v0.models.{Library, SyncEvent, VersionForm}
import io.flow.postgresql.OrderBy
import io.flow.common.v0.models.UserReference
import com.google.inject.Provider
import db.generated.ProjectLibraryForm
import io.flow.dependency.actors.ProjectActor

case class InternalProjectLibrary(db: generated.ProjectLibrary) {
  val id: String = db.id
  val organizationId: String = db.organizationId
  val projectId: String = db.projectId
  val groupId: String = db.groupId
  val artifactId: String = db.artifactId
}

@Singleton
class InternalProjectLibrariesDao @Inject()(
  dao: generated.ProjectLibrariesDao,
  projectsDaoProvider: Provider[ProjectsDao],
  membershipsDaoProvider: Provider[MembershipsDao],
  @javax.inject.Named("project-actor") projectActor: akka.actor.ActorRef
){

  private[db] def validate(
    user: UserReference,
    form: ProjectLibraryForm
  ): Seq[String] = {
    val groupIdErrors = if (form.groupId.trim.isEmpty) {
      Seq("Group ID cannot be empty")
    } else {
      Nil
    }

    val artifactIdErrors = if (form.artifactId.trim.isEmpty) {
      Seq("Artifact ID cannot be empty")
    } else {
      Nil
    }

    val versionErrors = if (form.version.trim.isEmpty) {
      Seq("Version cannot be empty")
    } else {
      Nil
    }

    val projectErrors = projectsDaoProvider.get.findById(Authorization.All, form.projectId) match {
      case None => Seq("Project not found")
      case Some(project) => {
        if (membershipsDaoProvider.get.isMemberByOrgId(project.organization.id, user)) {
          Nil
        } else {
          Seq("You are not authorized to edit this project")
        }
      }
    }

    val existsErrors = if (Seq(groupIdErrors, artifactIdErrors, versionErrors, projectErrors).flatten.isEmpty) {
      findByProjectIdAndGroupIdAndArtifactIdAndVersion(
        Authorization.All, form.projectId, form.groupId, form.artifactId, VersionForm(
          form.version, form.crossBuildVersion
        )
      ) match {
        case None => Nil
        case Some(_) => {
          Seq("Project library with this group id, artifact id, and version already exists")
        }
      }
    } else {
      Nil
    }

    projectErrors ++ groupIdErrors ++ artifactIdErrors ++ versionErrors ++ existsErrors
  }

  def upsert(createdBy: UserReference, form: ProjectLibraryForm): Either[Seq[String], InternalProjectLibrary] = {
    findByProjectIdAndGroupIdAndArtifactIdAndVersion(
      Authorization.All, form.projectId, form.groupId, form.artifactId, VersionForm(
        version = form.version,
        crossBuildVersion = form.crossBuildVersion
      )
    ) match {
      case None => {
        create(createdBy, form)
      }
      case Some(lib) => {
        Right(lib)
      }
    }
  }

  def create(createdBy: UserReference, form: ProjectLibraryForm): Either[Seq[String], InternalProjectLibrary] = {
    validate(createdBy, form) match {
      case Nil => {
        val id = dao.insert(createdBy, form)

        projectActor ! ProjectActor.Messages.ProjectLibraryCreated(form.projectId, id)

        Right(
          findById(Authorization.All, id).getOrElse {
            sys.error("Failed to create project library")
          }
        )
      }
      case errors => Left(errors)
    }
  }

  def removeLibrary(user: UserReference, projectLibrary: InternalProjectLibrary): Unit = {
    dao.update(user, projectLibrary.db, projectLibrary.db.form.copy(
      libraryId = None
    ))
    dao.deleteById(user, projectLibrary.id)
  }

  /**
    * Removes any project library ids for this project not specified in this list
    */
  def setIds(user: UserReference, projectId: String, projectBinaries: Seq[InternalProjectLibrary]): Unit = {
    val ids = projectBinaries.map(_.id).toSet
    findAll(Authorization.All, projectId = Some(projectId), limit = None, orderBy = None)
      .filterNot { pl => ids.contains(pl.id) }
      .foreach { pl => delete(user, pl) }
  }

  def setLibrary(user: UserReference, projectLibrary: InternalProjectLibrary, library: Library): Unit = {
    dao.update(user, projectLibrary.db, projectLibrary.db.form.copy(
      libraryId = Some(library.id)
    ))
  }

  def delete(deletedBy: UserReference, library: InternalProjectLibrary): Unit = {
    dao.delete(deletedBy, library.db)
    projectActor ! ProjectActor.Messages.ProjectLibraryDeleted(library.projectId, library.id, library.db.version)
  }

  def findByProjectIdAndGroupIdAndArtifactIdAndVersion(
    auth: Authorization,
    projectId: String,
    groupId: String,
    artifactId: String,
    version: VersionForm
  ): Option[InternalProjectLibrary] = {
    findAll(
      auth,
      projectId = Some(projectId),
      groupId = Some(groupId),
      artifactId = Some(artifactId),
      version = Some(version.version),
      crossBuildVersion = Some(version.crossBuildVersion),
      limit = Some(1),
      orderBy = None,
    ).headOption
  }

  def findById(auth: Authorization, id: String): Option[InternalProjectLibrary] = {
    findAll(auth, ids = Some(Seq(id)), limit = Some(1), orderBy = None).headOption
  }

  def findAllByProjectId(auth: Authorization, projectId: String): Seq[InternalProjectLibrary] = {
    findAll(auth, projectId = Some(projectId), limit = None, orderBy = None)
  }

  def findAll(
    auth: Authorization,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    projectId: Option[String] = None,
    projectIds: Option[Seq[String]] = None,
    libraryId: Option[String] = None,
    groupId: Option[String] = None,
    artifactId: Option[String] = None,
    version: Option[String] = None,
    crossBuildVersion: Option[Option[String]] = None,
    isSynced: Option[Boolean] = None,
    hasLibrary: Option[Boolean] = None,
    orderBy: Option[OrderBy],
    limit: Option[Long],
    offset: Long = 0
  ): Seq[InternalProjectLibrary] = {
    dao.findAll(
      ids = ids,
      projectId = projectId,
      libraryId = libraryId,
      groupId = groupId,
      artifactId = artifactId,
      version = version,
      orderBy = orderBy,
      limit = limit,
      offset = offset,
    ) { q =>
      q
        .and(auth.organizationProjects("organization_id", "project_id").sql)
        .equals("id", id)
        .optionalIn("project_id", projectIds)
        .and(
          crossBuildVersion.map {
            case None => "cross_build_version is null"
            case Some(_) => "cross_build_version = {cross_build_version}"
          }
        )
        .bind("cross_build_version", crossBuildVersion.flatten)
        .and(
          isSynced.map { value =>
            val clause = "select 1 from syncs where object_id = id and event = {sync_event_completed}"
            if (value) {
              s"exists ($clause)"
            } else {
              s"not exists ($clause)"
            }
          }
        )
        .bind("sync_event_completed", isSynced.map(_ => SyncEvent.Completed.toString))
        .nullBoolean("library_id", hasLibrary)
    }.map(InternalProjectLibrary.apply)
  }

}
