package db

import io.flow.dependency.v0.models.{BinaryType, SyncEvent}
import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import java.util.UUID

class ProjectBinariesDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val org = createOrganization()
  lazy val project = createProject(org)
  lazy val projectBinary = createProjectBinary(project)

  "validate" must {

    "catch empty name" in {
      ProjectBinariesDao.validate(
        systemUser,
        createProjectBinaryForm(project).copy(name = BinaryType.UNDEFINED("   "))
      ) must be(Seq("Name cannot be empty"))
    }

    "catch empty version" in {
      ProjectBinariesDao.validate(
        systemUser,
        createProjectBinaryForm(project).copy(version = "   ")
      ) must be(Seq("Version cannot be empty"))
    }

    "catch invalid project" in {
      ProjectBinariesDao.validate(
        systemUser,
        createProjectBinaryForm(project).copy(projectId = UUID.randomUUID.toString())
      ) must be(Seq("Project not found"))
    }

    "catch project we cannot access" in {
      ProjectBinariesDao.validate(
        createUser(),
        createProjectBinaryForm(project)
      ) must be(Seq("You are not authorized to edit this project"))
    }

  }

  "create" in {
    val form = createProjectBinaryForm(project)
    val projectBinary = createProjectBinary(project)(form)

    val one = ProjectBinariesDao.findById(Authorization.All, projectBinary.id).getOrElse {
      sys.error("Failed to create project binary")
    }

    one.project.id must be(project.id)
    one.name must be(projectBinary.name)
    one.version must be(projectBinary.version)
    one.path must be(projectBinary.path)
  }

  "upsert" in {
    val form = createProjectBinaryForm(project)

    val one = create(ProjectBinariesDao.upsert(systemUser, form))
    create(ProjectBinariesDao.upsert(systemUser, form)).id must be(one.id)
    create(ProjectBinariesDao.upsert(systemUser, form)).id must be(one.id)
  }

  "setBinary" in {
    val projectBinary = createProjectBinary(project)
    val binary = createBinary(org)
    ProjectBinariesDao.setBinary(systemUser, projectBinary, binary)
    ProjectBinariesDao.findById(Authorization.All, projectBinary.id).flatMap(_.binary.map(_.id)) must be(Some(binary.id))

    ProjectBinariesDao.removeBinary(systemUser, projectBinary)
    ProjectBinariesDao.findById(Authorization.All, projectBinary.id).flatMap(_.binary) must be(None)
  }

  "setIds" in {
    val projectBinary = createProjectBinary(project)

    ProjectBinariesDao.setIds(systemUser, projectBinary.project.id, Seq(projectBinary))
    ProjectBinariesDao.findById(Authorization.All, projectBinary.id).map(_.id) must be(Some(projectBinary.id))

    ProjectBinariesDao.setIds(systemUser, projectBinary.project.id, Nil)
    ProjectBinariesDao.findById(Authorization.All, projectBinary.id).flatMap(_.binary) must be(None)
}

  "delete" in {
    val projectBinary = createProjectBinary(project)
    ProjectBinariesDao.delete(systemUser, projectBinary)
    ProjectBinariesDao.findById(Authorization.All, projectBinary.id) must be(None)
  }

  "findAll" must {

    "filter by id" in {
      ProjectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id)).map(_.id) must be(
        Seq(projectBinary.id)
      )
      ProjectBinariesDao.findAll(Authorization.All, projectId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by ids" in {
      val other = createProjectBinary(project)

      ProjectBinariesDao.findAll(Authorization.All, ids = Some(Seq(projectBinary.id, other.id))).map(_.id).sorted must be(
        Seq(projectBinary.id, other.id).sorted
      )

      ProjectBinariesDao.findAll(Authorization.All, ids = Some(Seq(projectBinary.id, UUID.randomUUID.toString))).map(_.id).sorted must be(
        Seq(projectBinary.id).sorted
      )

      ProjectBinariesDao.findAll(Authorization.All, ids = Some(Nil)) must be(Nil)
    }

    "filter by projectId" in {
      ProjectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), projectId = Some(project.id)).map(_.id) must be(
        Seq(projectBinary.id)
      )
      ProjectBinariesDao.findAll(Authorization.All, projectId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by binaryId" in {
      val binary = createBinary(org)
      val projectBinary = createProjectBinary(project)
      ProjectBinariesDao.setBinary(systemUser, projectBinary, binary)

      ProjectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), binaryId = Some(binary.id)).map(_.id) must be(
        Seq(projectBinary.id)
      )
      ProjectBinariesDao.findAll(Authorization.All, binaryId = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by name" in {
      ProjectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), name = Some(projectBinary.name)).map(_.id) must be(
        Seq(projectBinary.id)
      )
      ProjectBinariesDao.findAll(Authorization.All, name = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by version" in {
      ProjectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), version = Some(projectBinary.version)).map(_.id) must be(
        Seq(projectBinary.id)
      )
      ProjectBinariesDao.findAll(Authorization.All, version = Some(UUID.randomUUID.toString)) must be(Nil)
    }

    "filter by isSynced" in {
      createSync(createSyncForm(objectId = projectBinary.id, event = SyncEvent.Completed))

      ProjectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), isSynced = Some(true)).map(_.id) must be(
        Seq(projectBinary.id)
      )
      ProjectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), isSynced = Some(false)) must be(Nil)
    }

    "filter by hasBinary" in {
      val projectBinary = createProjectBinary(project)

      ProjectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), hasBinary = Some(true)) must be(Nil)
      ProjectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), hasBinary = Some(false)).map(_.id) must be(
        Seq(projectBinary.id)
      )

      ProjectBinariesDao.setBinary(systemUser, projectBinary, createBinary(org))

      ProjectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), hasBinary = Some(true)).map(_.id) must be(
        Seq(projectBinary.id)
      )
      ProjectBinariesDao.findAll(Authorization.All, id = Some(projectBinary.id), hasBinary = Some(false)) must be(Nil)
    }
  }


}
