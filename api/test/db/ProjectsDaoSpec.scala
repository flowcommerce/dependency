package db

import java.util.UUID
import io.flow.dependency.v0.models.{Organization, Project, Scms, Visibility}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import util.DependencySpec

class ProjectsDaoSpec extends DependencySpec with Eventually with IntegrationPatience {

  private[this] lazy val org: Organization = createOrganization()
  private[this] lazy val project1: Project = createProject(org)
  private[this] lazy val project2: Project = createProject(org)

  private[this] def findAll(
    auth: Authorization = Authorization.All,
    id: Option[String] = None,
    ids: Option[Seq[String]] = None,
    organizationId: Option[String] = None,
    name: Option[String] = None,
    organizationKey: Option[String] = None,
    groupId: Option[String] = None,
    artifactId: Option[String] = None,
    binary: Option[String] = None,
    binaryId: Option[String] = None,
    version: Option[String] = None,
    libraryId: Option[String] = None,
    limit: Option[Long] = None,
    offset: Long = 0
  ): Seq[Project] = {
    projectsDao.findAll(
      auth,
      id = id,
      ids = ids,
      organizationId = organizationId,
      name = name,
      organizationKey = organizationKey,
      groupId = groupId,
      artifactId = artifactId,
      binary = binary,
      binaryId = binaryId,
      version = version,
      libraryId = libraryId,
      limit = limit,
      offset = offset
    )
  }

  "findByOrganizationIdAndName" in {
    projectsDao.findByOrganizationKeyAndName(Authorization.All, org.key, project1.name).map(_.id) must be(
      Some(project1.id)
    )

    projectsDao.findByOrganizationKeyAndName(Authorization.All, createTestKey(), project1.name) must be(None)
    projectsDao.findByOrganizationKeyAndName(Authorization.All, org.key, createTestName()) must be(None)
  }

  "findById" in {
    projectsDao.findById(Authorization.All, project1.id).map(_.id) must be(
      Some(project1.id)
    )

    projectsDao.findById(Authorization.All, UUID.randomUUID.toString) must be(None)
  }

  "update" in {
    val form = createProjectForm(org)
    val project = createProject(org)(form)
    projectsDao.update(systemUser, project, form.copy(uri = "http://github.com/mbryzek/test"))
    projectsDao.findById(Authorization.All, project.id).map(_.uri) must be(Some("http://github.com/mbryzek/test"))
  }

  "update allows name change" in {
    val form = createProjectForm(org)
    val project = createProject(org)(form)
    val newName = project.name + "2"
    val updated = projectsDao.update(systemUser, project, form.copy(name = newName)).rightValue
    updated.id must be(project.id)
    updated.name must be(newName)
  }

  "create" must {
    "validates SCMS" in {
      val form = createProjectForm(org).copy(scms = Scms.UNDEFINED("other"))
      projectsDao.create(systemUser, form) must be(Left(Seq("Scms not found")))
    }

    "validates SCMS URI" in {
      val form = createProjectForm(org).copy(scms = Scms.Github, uri = "http://github.com/mbryzek")
      projectsDao.create(systemUser, form) must be(
        Left(Seq("Invalid uri path[http://github.com/mbryzek] missing project name"))
      )
    }

    "validates empty name" in {
      val form = createProjectForm(org).copy(name = "   ")
      projectsDao.create(systemUser, form) must be(Left(Seq("Name cannot be empty")))
    }

    "validates duplicate names" in {
      val project = createProject(org)
      val form = createProjectForm(org).copy(name = project.name.toString.toUpperCase)
      projectsDao.create(systemUser, form) must be(Left(Seq("Project with this name already exists")))
      projectsDao.validate(systemUser, form, existing = Some(project)) must be(Nil)

      val org2 = createOrganization()
      val form2 = createProjectForm(org2).copy(name = project.name)
      projectsDao.validate(systemUser, form2) must be(Nil)
    }

    "validates empty uri" in {
      val form = createProjectForm(org).copy(uri = "   ")
      projectsDao.create(systemUser, form) must be(Left(Seq("Uri cannot be empty")))
    }

    "create with specified branch name" in {
      val branch = createTestId()
      val form = createProjectForm(org).copy(branch = branch)
      val project = rightOrErrors(projectsDao.create(systemUser, form))
      project.branch must be(branch)
    }
  }

  "findAll" must {

    "ids" in {
      findAll(ids = Some(Seq(project1.id, project2.id))).map(_.id).sorted must be(
        Seq(project1.id, project2.id).sorted
      )

      findAll(ids = Some(Nil)) must be(Nil)
      findAll(ids = Some(Seq(UUID.randomUUID.toString))) must be(Nil)
      findAll(ids = Some(Seq(project1.id, UUID.randomUUID.toString))).map(_.id) must be(Seq(project1.id))
    }

    "name" in {
      findAll(name = Some(project1.name.toUpperCase)).map(_.id) must be(
        Seq(project1.id)
      )

      findAll(name = Some(UUID.randomUUID.toString)).map(_.id) must be(Nil)
    }

    "organizationId" in {
      findAll(id = Some(project1.id), organizationId = Some(org.id)).map(_.id) must be(
        Seq(project1.id)
      )

      findAll(id = Some(project1.id), organizationId = Some(createOrganization().id)) must be(Nil)
    }

    "organizationKey" in {
      findAll(id = Some(project1.id), organizationKey = Some(org.key)).map(_.id) must be(
        Seq(project1.id)
      )

      findAll(id = Some(project1.id), organizationKey = Some(createOrganization().key)) must be(Nil)
    }

    "with library" must {

      "groupId" in {
        val (project, version) = createProjectWithLibrary(org)

        findAll(id = Some(project.id), groupId = Some(version.library.groupId)).map(_.id) must be(
          Seq(project.id)
        )

        findAll(id = Some(project.id), groupId = Some(UUID.randomUUID.toString)).map(_.id) must be(Nil)
      }

      "artifactId" in {
        val (project, version) = createProjectWithLibrary(org)

        findAll(id = Some(project.id), artifactId = Some(version.library.artifactId)).map(_.id) must be(
          Seq(project.id)
        )

        findAll(id = Some(project.id), artifactId = Some(UUID.randomUUID.toString)).map(_.id) must be(Nil)
      }

      "version" in {
        val (project, version) = createProjectWithLibrary(org)

        findAll(id = Some(project.id), version = Some(version.version)).map(_.id) must be(
          Seq(project.id)
        )

        findAll(id = Some(project.id), version = Some(UUID.randomUUID.toString)).map(_.id) must be(Nil)
      }

      "libraryId" in {
        val (project, version) = createProjectWithLibrary(org)

        findAll(id = Some(project.id), libraryId = Some(version.library.id)).map(_.id) must be(
          Seq(project.id)
        )

        findAll(id = Some(project.id), libraryId = Some(UUID.randomUUID.toString)).map(_.id) must be(Nil)
      }
    }

    "with binary" must {

      "binary name" in {
        val (project, version) = createProjectWithBinary(org)

        eventually {
          findAll(id = Some(project.id), binary = Some(version.binary.name.toString)).map(_.id) must be(
            Seq(project.id)
          )
          findAll(id = Some(project.id), binary = Some(UUID.randomUUID.toString)) must be(Nil)
        }
      }

      "binary id" in {
        val (project, version) = createProjectWithBinary(org)

        eventually {
          findAll(id = Some(project.id), binaryId = Some(version.binary.id)).map(_.id) must be(
            Seq(project.id)
          )
          findAll(id = Some(project.id), binaryId = Some(UUID.randomUUID.toString)) must be(Nil)
        }
      }
    }

    "authorization for public projects" in {
      val user = createUser()
      val org = createOrganization(user = user)
      val project = createProject(org)(createProjectForm(org).copy(visibility = Visibility.Public))

      findAll(Authorization.PublicOnly, id = Some(project.id)).map(_.id) must be(Seq(project.id))
      findAll(id = Some(project.id)).map(_.id) must be(Seq(project.id))
      findAll(Authorization.Organization(org.id), id = Some(project.id)).map(_.id) must be(Seq(project.id))
      findAll(Authorization.Organization(createOrganization().id), id = Some(project.id)).map(_.id) must be(
        Seq(project.id)
      )
      findAll(Authorization.User(user.id), id = Some(project.id)).map(_.id) must be(Seq(project.id))
    }

    "authorization for private projects" in {
      val user = createUser()
      val org = createOrganization(user = user)
      val project = createProject(org)(createProjectForm(org).copy(visibility = Visibility.Private))

      findAll(Authorization.PublicOnly, id = Some(project.id)) must be(Nil)
      findAll(id = Some(project.id)).map(_.id) must be(Seq(project.id))
      findAll(Authorization.Organization(org.id), id = Some(project.id)).map(_.id) must be(Seq(project.id))
      findAll(Authorization.Organization(createOrganization().id), id = Some(project.id)) must be(Nil)
      findAll(Authorization.User(user.id), id = Some(project.id)).map(_.id) must be(Seq(project.id))
      findAll(Authorization.User(createUser().id), id = Some(project.id)) must be(Nil)
    }

  }
}
