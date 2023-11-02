package db

import java.util.UUID
import io.flow.dependency.v0.models.SyncEvent
import util.DependencySpec

class InternalProjectLibrariesDaoSpec extends DependencySpec {

  private[this] lazy val org = createOrganization()
  private[this] lazy val project = createProject(org)
  private[this] lazy val projectLibrary = createProjectLibrary(project)

  "validate" must {

    "catch empty group id" in {
      projectLibrariesDao.validate(
        systemUser,
        createProjectLibraryForm(project).copy(groupId = "   ")
      ) must be(Seq("Group ID cannot be empty"))
    }

    "catch empty artifact id" in {
      projectLibrariesDao.validate(
        systemUser,
        createProjectLibraryForm(project).copy(artifactId = "   ")
      ) must be(Seq("Artifact ID cannot be empty"))
    }

    "catch empty version" in {
      projectLibrariesDao.validate(
        systemUser,
        createProjectLibraryForm(project).copy(version = "   ")
      ) must be(Seq("Version cannot be empty"))
    }

    "catch invalid project" in {
      projectLibrariesDao.validate(
        systemUser,
        createProjectLibraryForm(project).copy(projectId = UUID.randomUUID.toString)
      ) must be(Seq("Project not found"))
    }

    "catch project we cannot access" in {
      projectLibrariesDao.validate(
        createUser(),
        createProjectLibraryForm(project)
      ) must be(Seq("You are not authorized to edit this project"))
    }

  }

  "create" in {
    val form = createProjectLibraryForm(project, crossBuildVersion = Some("2.11"))
    val projectLibrary = createProjectLibrary(project)(form)

    val one = projectLibrariesDao.findById(Authorization.All, projectLibrary.id).getOrElse {
      sys.error("Failed to create project library")
    }

    one.projectId must be(project.id)
    one.groupId must be(projectLibrary.groupId)
    one.artifactId must be(projectLibrary.artifactId)
    one.db.version must be(projectLibrary.db.version)
    one.db.crossBuildVersion must be(Some("2.11"))
    one.db.path must be(projectLibrary.db.path)
  }

  "upsert" in {
    val form = createProjectLibraryForm(project)

    val one = rightOrErrors(projectLibrariesDao.upsert(systemUser, form))
    one.db.crossBuildVersion must be(None)
    rightOrErrors(projectLibrariesDao.upsert(systemUser, form)).id must be(one.id)

    val form210 = form.copy(
      crossBuildVersion = Some("2.10")
    )
    val two = rightOrErrors(projectLibrariesDao.upsert(systemUser, form210))
    two.db.crossBuildVersion must be(Some("2.10"))
    rightOrErrors(projectLibrariesDao.upsert(systemUser, form210)).id must be(two.id)

    val form211 = form.copy(
      crossBuildVersion = Some("2.11")
    )
    val three = rightOrErrors(projectLibrariesDao.upsert(systemUser, form211))
    three.db.crossBuildVersion must be(Some("2.11"))
    rightOrErrors(projectLibrariesDao.upsert(systemUser, form211)).id must be(three.id)

    val other = rightOrErrors(projectLibrariesDao.upsert(systemUser, form.copy(groupId = form.groupId + ".other")))
    other.groupId must be(form.groupId + ".other")
  }

  "setLibrary" in {
    val projectLibrary = createProjectLibrary(project)
    val library = createLibrary(org)
    projectLibrariesDao.setLibrary(systemUser, projectLibrary, library)
    projectLibrariesDao.findById(Authorization.All, projectLibrary.id).flatMap(_.db.libraryId) must be(Some(library.id))

    projectLibrariesDao.removeLibrary(systemUser, projectLibrary)
    projectLibrariesDao.findById(Authorization.All, projectLibrary.id).flatMap(_.db.libraryId) must be(None)
  }

  "setIds" in {
    val projectLibrary = createProjectLibrary(project)

    projectLibrariesDao.setIds(systemUser, projectLibrary.projectId, Seq(projectLibrary))
    projectLibrariesDao.findById(Authorization.All, projectLibrary.id).map(_.id) must be(Some(projectLibrary.id))

    projectLibrariesDao.setIds(systemUser, projectLibrary.projectId, Nil)
    projectLibrariesDao.findById(Authorization.All, projectLibrary.id).flatMap(_.db.libraryId) must be(None)
  }

  "delete" in {
    val projectLibrary = createProjectLibrary(project)
    projectLibrariesDao.delete(systemUser, projectLibrary)
    projectLibrariesDao.findById(Authorization.All, projectLibrary.id) must be(None)
  }

  "findAll" must {

    "filter by id" in {
      projectLibrariesDao
        .findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), orderBy = None)
        .map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(
        Authorization.All,
        limit = None,
        projectId = Some(UUID.randomUUID.toString),
        orderBy = None
      ) must be(Nil)
    }

    "filter by ids" in {
      val other = createProjectLibrary(project)

      projectLibrariesDao
        .findAll(Authorization.All, limit = None, ids = Some(Seq(projectLibrary.id, other.id)), orderBy = None)
        .map(_.id)
        .sorted must be(
        Seq(projectLibrary.id, other.id).sorted
      )

      projectLibrariesDao
        .findAll(
          Authorization.All,
          limit = None,
          ids = Some(Seq(projectLibrary.id, UUID.randomUUID.toString)),
          orderBy = None
        )
        .map(_.id)
        .sorted must be(
        Seq(projectLibrary.id).sorted
      )

      projectLibrariesDao.findAll(Authorization.All, limit = None, ids = Some(Nil), orderBy = None) must be(Nil)
    }

    "filter by projectId" in {
      projectLibrariesDao
        .findAll(
          Authorization.All,
          limit = None,
          id = Some(projectLibrary.id),
          projectId = Some(project.id),
          orderBy = None
        )
        .map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(
        Authorization.All,
        limit = None,
        projectId = Some(UUID.randomUUID.toString),
        orderBy = None
      ) must be(Nil)
    }

    "filter by libraryId" in {
      val library = createLibrary(org)
      val projectLibrary = createProjectLibrary(project)
      projectLibrariesDao.setLibrary(systemUser, projectLibrary, library)

      projectLibrariesDao
        .findAll(
          Authorization.All,
          limit = None,
          id = Some(projectLibrary.id),
          libraryId = Some(library.id),
          orderBy = None
        )
        .map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(
        Authorization.All,
        limit = None,
        libraryId = Some(UUID.randomUUID.toString),
        orderBy = None
      ) must be(Nil)
    }

    "filter by groupId" in {
      projectLibrariesDao
        .findAll(
          Authorization.All,
          limit = None,
          id = Some(projectLibrary.id),
          groupId = Some(projectLibrary.groupId),
          orderBy = None
        )
        .map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(
        Authorization.All,
        limit = None,
        groupId = Some(UUID.randomUUID.toString),
        orderBy = None
      ) must be(Nil)
    }

    "filter by artifactId" in {
      projectLibrariesDao
        .findAll(
          Authorization.All,
          limit = None,
          id = Some(projectLibrary.id),
          artifactId = Some(projectLibrary.artifactId),
          orderBy = None
        )
        .map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(
        Authorization.All,
        limit = None,
        artifactId = Some(UUID.randomUUID.toString),
        orderBy = None
      ) must be(Nil)
    }

    "filter by version" in {
      projectLibrariesDao
        .findAll(
          Authorization.All,
          limit = None,
          id = Some(projectLibrary.id),
          version = Some(projectLibrary.db.version),
          orderBy = None
        )
        .map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(
        Authorization.All,
        limit = None,
        version = Some(UUID.randomUUID.toString),
        orderBy = None
      ) must be(Nil)
    }

    "filter by crossBuildVersion" in {
      val projectLibrary =
        createProjectLibrary(project)(createProjectLibraryForm(project, crossBuildVersion = Some("2.11")))

      projectLibrariesDao
        .findAll(
          Authorization.All,
          limit = None,
          id = Some(projectLibrary.id),
          crossBuildVersion = Some(Some("2.11")),
          orderBy = None
        )
        .map(_.id) must be(
        Seq(projectLibrary.id)
      )

      projectLibrariesDao.findAll(
        Authorization.All,
        limit = None,
        id = Some(projectLibrary.id),
        crossBuildVersion = Some(Some(UUID.randomUUID.toString)),
        orderBy = None
      ) must be(Nil)
      projectLibrariesDao.findAll(
        Authorization.All,
        limit = None,
        id = Some(projectLibrary.id),
        crossBuildVersion = Some(None),
        orderBy = None
      ) must be(Nil)
    }

    "filter by isSynced" in {
      createSync(createSyncForm(objectId = projectLibrary.id, event = SyncEvent.Completed))

      projectLibrariesDao
        .findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), isSynced = Some(true), orderBy = None)
        .map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(
        Authorization.All,
        limit = None,
        id = Some(projectLibrary.id),
        isSynced = Some(false),
        orderBy = None
      ) must be(Nil)
    }

    "filter by hasLibrary" in {
      val projectLibrary = createProjectLibrary(project)

      projectLibrariesDao.findAll(
        Authorization.All,
        limit = None,
        id = Some(projectLibrary.id),
        hasLibrary = Some(true),
        orderBy = None
      ) must be(Nil)
      projectLibrariesDao
        .findAll(
          Authorization.All,
          limit = None,
          id = Some(projectLibrary.id),
          hasLibrary = Some(false),
          orderBy = None
        )
        .map(_.id) must be(
        Seq(projectLibrary.id)
      )

      projectLibrariesDao.setLibrary(systemUser, projectLibrary, createLibrary(org))

      projectLibrariesDao
        .findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), hasLibrary = Some(true), orderBy = None)
        .map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(
        Authorization.All,
        limit = None,
        id = Some(projectLibrary.id),
        hasLibrary = Some(false),
        orderBy = None
      ) must be(Nil)
    }
  }

}
