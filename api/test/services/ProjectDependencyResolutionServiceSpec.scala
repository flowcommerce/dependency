package services

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import util.DependencySpec

class ProjectDependencyResolutionServiceSpec extends DependencySpec
  with Eventually with IntegrationPatience {

  def projectDependencyResolutionService: ProjectDependencyResolutionServiceImpl = init[ProjectDependencyResolutionService].asInstanceOf[ProjectDependencyResolutionServiceImpl]

  private[this] val defaultOrg = createOrganization()
  private[this] val defaultGroupId = createTestId()
  private[this] val libS3Project = {
    val p = createProject(defaultOrg)(
      createProjectForm(defaultOrg, name = "lib-s3")
    )
    upsertLibrary(groupId = defaultGroupId, artifactId = "lib-s3")
    projectsDao.toSummary(p)
  }

  private[this] val libInvoiceProject = {
    println("Creating lib invoice")
    val p = createProject(defaultOrg)(
      createProjectForm(defaultOrg, name = "lib-invoice")
    )
    println(s"Creating project library for id: ${p.id}")
    createProjectLibrary(p)(
      createProjectLibraryForm(p).copy(
        groupId = defaultGroupId,
        artifactId = "lib-s3",
      )
    )
    println("Creating lib invoice")
    projectsDao.toSummary(p)
  }

  "buildProjectInfo for no project" in {
    projectDependencyResolutionService.buildProjectInfo(Nil, groupId = createTestId()) must be(Nil)
  }

  "buildProjectInfo 'depends' and 'provides'" in {
    val all = projectDependencyResolutionService.buildProjectInfo(
      Seq(libS3Project, libInvoiceProject),
      groupId = defaultGroupId,
    )
    all.size must equal(2)
    val s3 = all.find(_.projectId == libS3Project.id).get
    s3.dependsOn must be(Nil)
    s3.provides.map(_.identifier) must equal(Seq(s"$defaultGroupId.lib-s3"))

    val invoice = all.find(_.projectId == libInvoiceProject.id).get
    invoice.dependsOn.map(_.identifier) must equal(Seq(s"$defaultGroupId.lib-s3"))
    invoice.provides must be(Nil)
  }

  "getByOrganization" in {
    val resolution = projectDependencyResolutionService.getByOrganizationKey(defaultOrg.key, defaultGroupId)
    eventually {
      resolution.resolved.size must be (2)
    }
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
