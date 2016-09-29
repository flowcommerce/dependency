package db

import com.bryzek.dependency.v0.models.{SyncEvent, VersionForm}
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class ProjectLibrariesDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val org = createOrganization()
  lazy val project = createProject(org)
  lazy val projectLibrary = createProjectLibrary(project)

  "validate" must {

    "catch empty group id" in {
      ProjectLibrariesDao.validate(
        systemUser,
        createProjectLibraryForm(project).copy(groupId = "   ")
      ) must be(Seq("Group ID cannot be empty"))
    }

    "catch empty artifact id" in {
      ProjectLibrariesDao.validate(
        systemUser,
        createProjectLibraryForm(project).copy(artifactId = "   ")
      ) must be(Seq("Artifact ID cannot be empty"))
    }

    "catch empty version" in {
      ProjectLibrariesDao.validate(
        systemUser,
        createProjectLibraryForm(project).copy(version = VersionForm(version = "   "))
      ) must be(Seq("Version cannot be empty"))
    }

    "catch invalid project" in {
      ProjectLibrariesDao.validate(
        systemUser,
        createProjectLibraryForm(project).copy(projectId = UUID.randomUUID.toString())
      ) must be(Seq("Project not found"))
    }

    "catch project we cannot access" in {
      ProjectLibrariesDao.validate(
        createUser(),
        createProjectLibraryForm(project)
      ) must be(Seq("You are not authorized to edit this project"))
    }

  }

  "create" in {
    val form = createProjectLibraryForm(project, crossBuildVersion = Some("2.11"))
    val projectLibrary = createProjectLibrary(project)(form)

    val one = ProjectLibrariesDao.findById(Authorization.All, projectLibrary.id).getOrElse {
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

    val one = create(ProjectLibrariesDao.upsert(systemUser, form))
    one.crossBuildVersion must be(None)
    create(ProjectLibrariesDao.upsert(systemUser, form)).id must be(one.id)

    val form210 = form.copy(
      version = form.version.copy(crossBuildVersion = Some("2.10"))
    )
    val two = create(ProjectLibrariesDao.upsert(systemUser, form210))
    two.crossBuildVersion must be(Some("2.10"))
    create(ProjectLibrariesDao.upsert(systemUser, form210)).id must be(two.id)

    val form211 = form.copy(
      version = form.version.copy(crossBuildVersion = Some("2.11"))
    )
    val three = create(ProjectLibrariesDao.upsert(systemUser, form211))
    three.crossBuildVersion must be(Some("2.11"))
    create(ProjectLibrariesDao.upsert(systemUser, form211)).id must be(three.id)

    val other = create(ProjectLibrariesDao.upsert(systemUser, form.copy(groupId = form.groupId + ".other")))
    other.groupId must be(form.groupId + ".other")
  }

  "setLibrary" in {
    val projectLibrary = createProjectLibrary(project)
    val library = createLibrary(org)
    ProjectLibrariesDao.setLibrary(systemUser, projectLibrary, library)
    ProjectLibrariesDao.findById(Authorization.All, projectLibrary.id).flatMap(_.library.map(_.id)) must be(Some(library.id))

    ProjectLibrariesDao.removeLibrary(systemUser, projectLibrary)
    ProjectLibrariesDao.findById(Authorization.All, projectLibrary.id).flatMap(_.library) must be(None)
  }

  "setIds" in {
    val projectLibrary = createProjectLibrary(project)

    ProjectLibrariesDao.setIds(systemUser, projectLibrary.project.id, Seq(projectLibrary))
    ProjectLibrariesDao.findById(Authorization.All, projectLibrary.id).map(_.id) must be(Some(projectLibrary.id))

    ProjectLibrariesDao.setIds(systemUser, projectLibrary.project.id, Nil)
    ProjectLibrariesDao.findById(Authorization.All, projectLibrary.id).flatMap(_.library) must be(None)
  }

  "delete" in {
    val projectLibrary = createProjectLibrary(project)
    ProjectLibrariesDao.delete(systemUser, projectLibrary)
    ProjectLibrariesDao.findById(Authorization.All, projectLibrary.id) must be(None)
  }

  "findAll" must {

    "filter by id" in {
      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      ProjectLibrariesDao.findAll(Authorization.All, projectId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by ids" in {
      val other = createProjectLibrary(project)

      ProjectLibrariesDao.findAll(Authorization.All, ids = Some(Seq(projectLibrary.id, other.id))).map(_.id).sorted must be(
        Seq(projectLibrary.id, other.id).sorted
      )

      ProjectLibrariesDao.findAll(Authorization.All, ids = Some(Seq(projectLibrary.id, UUID.randomUUID.toString))).map(_.id).sorted must be(
        Seq(projectLibrary.id).sorted
      )

      ProjectLibrariesDao.findAll(Authorization.All, ids = Some(Nil)) must be(Nil)
    }

    "filter by projectId" in {
      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id), projectId = Some(project.id)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      ProjectLibrariesDao.findAll(Authorization.All, projectId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by libraryId" in {
      val library = createLibrary(org)
      val projectLibrary = createProjectLibrary(project)
      ProjectLibrariesDao.setLibrary(systemUser, projectLibrary, library)

      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id), libraryId = Some(library.id)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      ProjectLibrariesDao.findAll(Authorization.All, libraryId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by groupId" in {
      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id), groupId = Some(projectLibrary.groupId)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      ProjectLibrariesDao.findAll(Authorization.All, groupId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by artifactId" in {
      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id), artifactId = Some(projectLibrary.artifactId)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      ProjectLibrariesDao.findAll(Authorization.All, artifactId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by version" in {
      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id), version = Some(projectLibrary.version)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      ProjectLibrariesDao.findAll(Authorization.All, version = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by crossBuildVersion" in {
      val projectLibrary = createProjectLibrary(project)(createProjectLibraryForm(project, crossBuildVersion = Some("2.11")))

      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id), crossBuildVersion = Some(Some("2.11"))).map(_.id) must be(
        Seq(projectLibrary.id)
      )

      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id), crossBuildVersion = Some(Some(UUID.randomUUID.toString))) must be(Nil)
      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id), crossBuildVersion = Some(None)) must be(Nil)
    }

    "filter by isSynced" in {
      createSync(createSyncForm(objectId = projectLibrary.id, event = SyncEvent.Completed))

      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id), isSynced = Some(true)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id), isSynced = Some(false)) must be(Nil)
    }

    "filter by hasLibrary" in {
      val projectLibrary = createProjectLibrary(project)

      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id), hasLibrary = Some(true)) must be(Nil)
      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id), hasLibrary = Some(false)).map(_.id) must be(
        Seq(projectLibrary.id)
      )

      ProjectLibrariesDao.setLibrary(systemUser, projectLibrary, createLibrary(org))

      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id), hasLibrary = Some(true)).map(_.id) must be(
        Seq(projectLibrary.id)
      )
      ProjectLibrariesDao.findAll(Authorization.All, id = Some(projectLibrary.id), hasLibrary = Some(false)) must be(Nil)
    }
  }


}
