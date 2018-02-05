package db

import java.util.UUID

import io.flow.dependency.v0.models.{SyncEvent, VersionForm}
import util.DependencySpec

class ProjectLibrariesDaoSpec extends DependencySpec {

  lazy val org = createOrganization()
  lazy val project = createProject(org)
  lazy val projectLibrary = createProjectLibrary(project)

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
        createProjectLibraryForm(project).copy(version = VersionForm(version = "   "))
      ) must be(Seq("Version cannot be empty"))
    }

    "catch invalid project" in {
      projectLibrariesDao.validate(
        systemUser,
        createProjectLibraryForm(project).copy(projectId = UUID.randomUUID.toString())
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

    one.project.id must be(project.id)
    one.groupId must be(projectLibrary.groupId)
    one.artifactId must be(projectLibrary.artifactId)
    one.version must be(projectLibrary.version)
    one.crossBuildVersion must be(Some("2.11"))
    one.path must be(projectLibrary.path)
  }

  "upsert" in {
    val form = createProjectLibraryForm(project)

    val one = create(projectLibrariesDao.upsert(systemUser, form))
    one.crossBuildVersion must be(None)
    create(projectLibrariesDao.upsert(systemUser, form)).id must be(one.id)

    val form210 = form.copy(
      version = form.version.copy(crossBuildVersion = Some("2.10"))
    )
    val two = create(projectLibrariesDao.upsert(systemUser, form210))
    two.crossBuildVersion must be(Some("2.10"))
    create(projectLibrariesDao.upsert(systemUser, form210)).id must be(two.id)

    val form211 = form.copy(
      version = form.version.copy(crossBuildVersion = Some("2.11"))
    )
    val three = create(projectLibrariesDao.upsert(systemUser, form211))
    three.crossBuildVersion must be(Some("2.11"))
    create(projectLibrariesDao.upsert(systemUser, form211)).id must be(three.id)

    val other = create(projectLibrariesDao.upsert(systemUser, form.copy(groupId = form.groupId + ".other")))
    other.groupId must be(form.groupId + ".other")
  }

  "setLibrary" in {
    val projectLibrary = createProjectLibrary(project)
    val library = createLibrary(org)
    projectLibrariesDao.setLibrary(systemUser, projectLibrary, library)
    projectLibrariesDao.findById(Authorization.All, projectLibrary.id).flatMap(_.library.map(_.id)) must be(Some(library.id))

    projectLibrariesDao.removeLibrary(systemUser, projectLibrary)
    projectLibrariesDao.findById(Authorization.All, projectLibrary.id).flatMap(_.library) must be(None)
  }

  "setIds" in {
    val projectLibrary = createProjectLibrary(project)

    projectLibrariesDao.setIds(systemUser, projectLibrary.project.id, Seq(projectLibrary))
    projectLibrariesDao.findById(Authorization.All, projectLibrary.id).map(_.id) must be(Some(projectLibrary.id))

    projectLibrariesDao.setIds(systemUser, projectLibrary.project.id, Nil)
    projectLibrariesDao.findById(Authorization.All, projectLibrary.id).flatMap(_.library) must be(None)
  }

  "delete" in {
    val projectLibrary = createProjectLibrary(project)
    projectLibrariesDao.delete(systemUser, projectLibrary)
    projectLibrariesDao.findById(Authorization.All, projectLibrary.id) must be(None)
  }

  "findAll" must {

    "filter by id" in {
      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(Authorization.All, limit = None, projectId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by ids" in {
      val other = createProjectLibrary(project)

      projectLibrariesDao.findAll(Authorization.All, limit = None, ids = Some(Seq(projectLibrary.id, other.id))).map(_.id).sorted must be(
        Seq(projectLibrary.id, other.id).sorted
      )

      projectLibrariesDao.findAll(Authorization.All, limit = None, ids = Some(Seq(projectLibrary.id, UUID.randomUUID.toString))).map(_.id).sorted must be(
        Seq(projectLibrary.id).sorted
      )

      projectLibrariesDao.findAll(Authorization.All, limit = None, ids = Some(Nil)) must be(Nil)
    }

    "filter by projectId" in {
      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), projectId = Some(project.id)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(Authorization.All, limit = None, projectId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by libraryId" in {
      val library = createLibrary(org)
      val projectLibrary = createProjectLibrary(project)
      projectLibrariesDao.setLibrary(systemUser, projectLibrary, library)

      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), libraryId = Some(library.id)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(Authorization.All, limit = None, libraryId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by groupId" in {
      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), groupId = Some(projectLibrary.groupId)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(Authorization.All, limit = None, groupId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by artifactId" in {
      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), artifactId = Some(projectLibrary.artifactId)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(Authorization.All, limit = None, artifactId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by version" in {
      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), version = Some(projectLibrary.version)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(Authorization.All, limit = None, version = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by crossBuildVersion" in {
      val projectLibrary = createProjectLibrary(project)(createProjectLibraryForm(project, crossBuildVersion = Some("2.11")))

      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), crossBuildVersion = Some(Some("2.11"))).map(_.id) must be(
        Seq(projectLibrary.id)
      )

      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), crossBuildVersion = Some(Some(UUID.randomUUID.toString))) must be(Nil)
      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), crossBuildVersion = Some(None)) must be(Nil)
    }

    "filter by isSynced" in {
      createSync(createSyncForm(objectId = projectLibrary.id, event = SyncEvent.Completed))

      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), isSynced = Some(true)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), isSynced = Some(false)) must be(Nil)
    }

    "filter by hasLibrary" in {
      val projectLibrary = createProjectLibrary(project)

      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), hasLibrary = Some(true)) must be(Nil)
      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), hasLibrary = Some(false)).map(_.id) must be(
        Seq(projectLibrary.id)
      )

      projectLibrariesDao.setLibrary(systemUser, projectLibrary, createLibrary(org))

      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), hasLibrary = Some(true)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      projectLibrariesDao.findAll(Authorization.All, limit = None, id = Some(projectLibrary.id), hasLibrary = Some(false)) must be(Nil)
    }
  }


}
