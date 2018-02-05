package db

import java.util.UUID

import io.flow.dependency.v0.models.{BinaryType, SyncEvent}
import util.DependencySpec

class ProjectBinariesDaoSpec extends DependencySpec {

  lazy val org = createOrganization()
  lazy val project = createProject(org)
  lazy val projectBinary = createProjectBinary(project)

  "validate" must {

    "catch empty name" in {
      projectBinariesDao.validate(
        systemUser,
        createProjectBinaryForm(project).copy(name = BinaryType.UNDEFINED("   "))
      ) must be(Seq("Name cannot be empty"))
    }

    "catch empty version" in {
      projectBinariesDao.validate(
        systemUser,
        createProjectBinaryForm(project).copy(version = "   ")
      ) must be(Seq("Version cannot be empty"))
    }

    "catch invalid project" in {
      projectBinariesDao.validate(
        systemUser,
        createProjectBinaryForm(project).copy(projectId = UUID.randomUUID.toString())
      ) must be(Seq("Project not found"))
    }

    "catch project we cannot access" in {
      projectBinariesDao.validate(
        createUser(),
        createProjectBinaryForm(project)
      ) must be(Seq("You are not authorized to edit this project"))
    }

  }

  "create" in {
    val form = createProjectBinaryForm(project)
    val projectBinary = createProjectBinary(project)(form)

    val one = projectBinariesDao.findById(Authorization.All, projectBinary.id).getOrElse {
      sys.error("Failed to create project binary")
    }

    one.project.id must be(project.id)
    one.name must be(projectBinary.name)
    one.version must be(projectBinary.version)
    one.path must be(projectBinary.path)
  }

  "upsert" in {
    val form = createProjectBinaryForm(project)

    val one = create(projectBinariesDao.upsert(systemUser, form))
    create(projectBinariesDao.upsert(systemUser, form)).id must be(one.id)
    create(projectBinariesDao.upsert(systemUser, form)).id must be(one.id)
  }

  "setBinary" in {
    val projectBinary = createProjectBinary(project)
    val binary = createBinary(org)
    projectBinariesDao.setBinary(systemUser, projectBinary, binary)
    projectBinariesDao.findById(Authorization.All, projectBinary.id).flatMap(_.binary.map(_.id)) must be(Some(binary.id))

    projectBinariesDao.removeBinary(systemUser, projectBinary)
    projectBinariesDao.findById(Authorization.All, projectBinary.id).flatMap(_.binary) must be(None)
  }

  "setIds" in {
    val projectBinary = createProjectBinary(project)

    projectBinariesDao.setIds(systemUser, projectBinary.project.id, Seq(projectBinary))
    projectBinariesDao.findById(Authorization.All, projectBinary.id).map(_.id) must be(Some(projectBinary.id))

    projectBinariesDao.setIds(systemUser, projectBinary.project.id, Nil)
    projectBinariesDao.findById(Authorization.All, projectBinary.id).flatMap(_.binary) must be(None)
  }

  "delete" in {
    val projectBinary = createProjectBinary(project)
    projectBinariesDao.delete(systemUser, projectBinary)
    projectBinariesDao.findById(Authorization.All, projectBinary.id) must be(None)
  }

  "findAll" must {

    "filter by id" in {
      projectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id)).map(_.id) must be(
        Seq(projectBinary.id)
      )
      projectBinariesDao.findAll(Authorization.All, projectId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by ids" in {
      val other = createProjectBinary(project)

      projectBinariesDao.findAll(Authorization.All, ids = Some(Seq(projectBinary.id, other.id))).map(_.id).sorted must be(
        Seq(projectBinary.id, other.id).sorted
      )

      projectBinariesDao.findAll(Authorization.All, ids = Some(Seq(projectBinary.id, UUID.randomUUID.toString))).map(_.id).sorted must be(
        Seq(projectBinary.id).sorted
      )

      projectBinariesDao.findAll(Authorization.All, ids = Some(Nil)) must be(Nil)
    }

    "filter by projectId" in {
      projectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), projectId = Some(project.id)).map(_.id) must be(
        Seq(projectBinary.id)
      )
      projectBinariesDao.findAll(Authorization.All, projectId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by binaryId" in {
      val binary = createBinary(org)
      val projectBinary = createProjectBinary(project)
      projectBinariesDao.setBinary(systemUser, projectBinary, binary)

      projectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), binaryId = Some(binary.id)).map(_.id) must be(
        Seq(projectBinary.id)
      )
      projectBinariesDao.findAll(Authorization.All, binaryId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by name" in {
      projectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), name = Some(projectBinary.name)).map(_.id) must be(
        Seq(projectBinary.id)
      )
      projectBinariesDao.findAll(Authorization.All, name = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by version" in {
      projectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), version = Some(projectBinary.version)).map(_.id) must be(
        Seq(projectBinary.id)
      )
      projectBinariesDao.findAll(Authorization.All, version = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by isSynced" in {
      createSync(createSyncForm(objectId = projectBinary.id, event = SyncEvent.Completed))

      projectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), isSynced = Some(true)).map(_.id) must be(
        Seq(projectBinary.id)
      )
      projectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), isSynced = Some(false)) must be(Nil)
    }

    "filter by hasBinary" in {
      val projectBinary = createProjectBinary(project)

      projectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), hasBinary = Some(true)) must be(Nil)
      projectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), hasBinary = Some(false)).map(_.id) must be(
        Seq(projectBinary.id)
      )

      projectBinariesDao.setBinary(systemUser, projectBinary, createBinary(org))

      projectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), hasBinary = Some(true)).map(_.id) must be(
        Seq(projectBinary.id)
      )
      projectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), hasBinary = Some(false)) must be(Nil)
    }
  }


}
