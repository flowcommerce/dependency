package services

import db.Authorization
import org.scalatest.BeforeAndAfter
import util.DependencySpec

class ProjectDependencyResolutionServiceSpec extends DependencySpec with BeforeAndAfter {

  private[this] lazy val projectDependencyResolutionService = init[ProjectDependencyResolutionServiceImpl]

  private[this] val defaultOrg = createOrganization()
  private[this] val defaultGroupId = createTestId()
  private[this] lazy val libS3Project = {
    val p = createProject(defaultOrg)(
      createProjectForm(defaultOrg, name = "lib-s3"),
    )
    upsertLibrary(groupId = defaultGroupId, artifactId = "lib-s3")
    projectsDao.toSummary(p)
  }

  private[this] lazy val libInvoiceProject = {
    val p = createProject(defaultOrg)(
      createProjectForm(defaultOrg, name = "lib-invoice"),
    )
    println(s"Creating project library for id: ${p.id}")
    createProjectLibrary(p)(
      createProjectLibraryForm(p).copy(
        groupId = defaultGroupId,
        artifactId = "lib-s3",
      ),
    )
    projectsDao.toSummary(p)
  }

  before {
    projectsDao.findAll(Authorization.All, limit = None).foreach(_ => projectsDao.delete(testUser, _))
    projectLibrariesDao
      .findAll(Authorization.All, orderBy = None, limit = None)
      .foreach(_ => projectLibrariesDao.delete(testUser, _))
    librariesDao.findAll(Authorization.All, limit = None).foreach(_ => librariesDao.delete(testUser, _))
  }

  "buildProjectInfo for no project" in {
    projectDependencyResolutionService.buildProjectInfo(Nil, groupId = createTestId()) must be(Nil)
  }

  "buildProjectInfo 'depends' and 'provides'" in {
    val all = projectDependencyResolutionService.buildProjectInfo(
      Seq(libS3Project, libInvoiceProject),
      groupId = defaultGroupId,
    )
    all.size must be(2)

    val s3 = all.find(_.projectId == libS3Project.id).get
    s3.dependsOn must be(Nil)
    s3.provides.map(_.identifier) must equal(Seq(s"$defaultGroupId.lib-s3"))

    val invoice = all.find(_.projectId == libInvoiceProject.id).get
    invoice.dependsOn.map(_.identifier) must equal(Seq(s"$defaultGroupId.lib-s3"))
    invoice.provides must be(Nil)
  }

  "getByOrganization" in {
    val resolution = projectDependencyResolutionService.getByOrganizationKey(defaultOrg.key, defaultGroupId)
    resolution.resolved.size must be(2)
    resolution.resolved.toList match {
      case a :: b :: Nil =>
        a.projects.map(_.name) must equal(Seq("lib-s3"))
        b.projects.map(_.name) must equal(Seq("lib-invoice"))

      case other =>
        sys.error(s"Expected two entries but found ${other.size} of $other")
    }
    resolution.unresolved must be(Nil)
  }
}
