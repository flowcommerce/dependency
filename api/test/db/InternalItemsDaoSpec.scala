package db

import java.util.UUID
import io.flow.dependency.v0.models._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import util.DependencySpec

class InternalItemsDaoSpec extends DependencySpec
  with helpers.TaskHelpers
  with BeforeAndAfterAll
  with Eventually with IntegrationPatience
{

  override def beforeAll(): Unit = {
    deleteAllNonProcessedTasks()
  }

  private[this] lazy val org = createOrganization()

  "replace" in {
    val project = createProjectSummary(org)
    eventually {
      itemsDao.findByObjectId(Authorization.All, project.id).get
    }
    val form = createItemForm(org)(project)
    val item1 = itemsDao.replace(systemUser, form)

    val item2 = itemsDao.replace(systemUser, form)
    item1.id must be(item2.id)
    item1.label must be(item2.label)

    val newLabel = createTestId()
    val item3 = itemsDao.replace(systemUser, form.copy(
      label = newLabel
    ))
    item3.id must be(item2.id)
    item3.label must be(newLabel)
  }

  "findById - binary" in {
    val binary = createBinary(org)

    val item = eventually {
      itemsDao.findByObjectId(Authorization.All, binary.id).get
    }

    itemsDao.findById(Authorization.All, item.id).get.id must be(item.id)
  }

  "findById - library" in {
    val library = createLibrary(org)

    val item = eventually {
      itemsDao.findByObjectId(Authorization.All, library.id).get
    }

    itemsDao.findById(Authorization.All, item.id).get.id must be(item.id)
  }

  "findById - project" in {
    val project = createProject(org)

    val item = eventually {
      itemsDao.findByObjectId(Authorization.All, project.id).get
    }

    itemsDao.findById(Authorization.All, item.id).get.id must be(item.id)
  }

  "findByObjectId" in {
    val binary = createBinary(org)
    eventually {
      itemsDao.findByObjectId(Authorization.All, binary.id).get
    }
  }

  "findAll by ids" in {
    val binary1 = createBinary(org)
    val binary2 = createBinary(org)

    val item1 = eventually {
      itemsDao.findByObjectId(Authorization.All, binary1.id).get
    }
    val item2 = eventually {
      itemsDao.findByObjectId(Authorization.All, binary2.id).get
    }

    itemsDao.findAll(Authorization.All, ids = Some(Seq(item1.id, item2.id)), limit = None).map(_.id).sorted must be(
      Seq(item1.id, item2.id).sorted
    )

    itemsDao.findAll(Authorization.All, ids = Some(Nil), limit = None) must be(Nil)
    itemsDao.findAll(Authorization.All, ids = Some(Seq(UUID.randomUUID.toString)), limit = None) must be(Nil)
    itemsDao.findAll(Authorization.All, ids = Some(Seq(item1.id, UUID.randomUUID.toString)), limit = None).map(_.id) must be(Seq(item1.id))
  }

  "supports binaries" in {
    val binary = createBinary(org)
    itemsDao.replaceBinary(systemUser, binary)

    val actual = itemsDao.findByObjectId(Authorization.All, binary.id).getOrElse {
      sys.error("Failed to create binary")
    }
    actual.label must be(binary.name.toString)
    actual.summary must be(
      BinarySummary(
        id = binary.id,
        organization = OrganizationSummary(org.id, org.key),
        name = binary.name
      )
    )

    itemsDao.findAll(Authorization.All, q = Some(binary.id.toString), limit = None).headOption.map(_.id) must be(Some(actual.id))
    itemsDao.findAll(Authorization.All, q = Some(UUID.randomUUID.toString), limit = None) must be(Nil)
  }

  "supports libraries" in {
    val library = createLibrary(org)()

    itemsDao.replaceLibrary(systemUser, library)
    val actual = itemsDao.findByObjectId(Authorization.All, library.id).getOrElse {
      sys.error("Failed to create library")
    }
    actual.label must be(Seq(library.groupId, library.artifactId).mkString("."))
    actual.summary must be(
      LibrarySummary(
        id = library.id,
        organization = OrganizationSummary(org.id, org.key),
        groupId = library.groupId,
        artifactId = library.artifactId
      )
    )

    itemsDao.findAll(Authorization.All, q = Some(library.id.toString), limit = None).headOption.map(_.id) must be(Some(actual.id))
    itemsDao.findAll(Authorization.All, q = Some(UUID.randomUUID.toString), limit = None) must be(Nil)
  }

  "supports projects" in {
    val project = createProject(org)

    itemsDao.replaceProject(systemUser, project)
    val actual = itemsDao.findByObjectId(Authorization.All, project.id).getOrElse {
      sys.error("Failed to create project")
    }
    actual.label must be(project.name)
    actual.summary must be(
      ProjectSummary(
        id = project.id,
        organization = OrganizationSummary(org.id, org.key),
        name = project.name
      )
    )

    itemsDao.findAll(Authorization.All, q = Some(project.id.toString), limit = None).headOption.map(_.id) must be(Some(actual.id))
    itemsDao.findAll(Authorization.All, q = Some(UUID.randomUUID.toString), limit = None) must be(Nil)
  }

  "authorization for public projects" in {
    val user = createUser()
    val org = createOrganization(user = user)
    val project = createProject(org)(createProjectForm(org).copy(visibility = Visibility.Public))
    val item = itemsDao.replaceProject(systemUser, project)

    itemsDao.findAll(Authorization.PublicOnly, objectId = Some(project.id), limit = None).map(_.id) must be(Seq(item.id))
    itemsDao.findAll(Authorization.All, objectId = Some(project.id), limit = None).map(_.id) must be(Seq(item.id))
    itemsDao.findAll(Authorization.Organization(org.id), objectId = Some(project.id), limit = None).map(_.id) must be(Seq(item.id))
    itemsDao.findAll(Authorization.Organization(createOrganization().id), objectId = Some(project.id), limit = None).map(_.id) must be(Seq(item.id))
    itemsDao.findAll(Authorization.User(user.id), objectId = Some(project.id), limit = None).map(_.id) must be(Seq(item.id))
  }

  "authorization for private projects" in {
    val user = createUser()
    val org = createOrganization(user = user)
    val project = createProject(org)(createProjectForm(org).copy(visibility = Visibility.Private))
    val item = itemsDao.replaceProject(systemUser, project)

    itemsDao.findAll(Authorization.PublicOnly, objectId = Some(project.id), limit = None) must be(Nil)
    itemsDao.findAll(Authorization.All, objectId = Some(project.id), limit = None).map(_.id) must be(Seq(item.id))
    itemsDao.findAll(Authorization.Organization(org.id), objectId = Some(project.id), limit = None).map(_.id) must be(Seq(item.id))
    itemsDao.findAll(Authorization.Organization(createOrganization().id), objectId = Some(project.id), limit = None) must be(Nil)
    itemsDao.findAll(Authorization.User(user.id), objectId = Some(project.id), limit = None).map(_.id) must be(Seq(item.id))
    itemsDao.findAll(Authorization.User(createUser().id), objectId = Some(project.id), limit = None) must be(Nil)
  }

}

