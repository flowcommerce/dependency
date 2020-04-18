package services

import util.DependencySpec

class ProjectDependencyResolutionServiceSpec extends DependencySpec {

  def projectDependencyResolutionService: ProjectDependencyResolutionServiceImpl = init[ProjectDependencyResolutionService].asInstanceOf[ProjectDependencyResolutionServiceImpl]

  private[this] val defaultOrg = createOrganization()
  private[this] val libS3Project = {
    val p = createProject(defaultOrg)(
      createProjectForm(defaultOrg, name = "lib-s3")
    )
    upsertLibrary(groupId = "io.flow", artifactId = "lib-s3")
    p
  }

  private[this] val libInvoiceProject = {
    val p = createProject(defaultOrg)(
      createProjectForm(defaultOrg, name = "lib-invoice")
    )
    createProjectLibrary(p)(
      createProjectLibraryForm(p).copy(
        groupId = "io.flow",
        artifactId = "lib-s3",
      )
    )
    p
  }

  "buildProjectInfo for no project" in {
    projectDependencyResolutionService.buildProjectInfo(Nil) must be(Nil)
  }

  "buildProjectInfo 'depends' and 'provides'" in {
    val all = projectDependencyResolutionService.buildProjectInfo(
      Seq(libS3Project, libInvoiceProject)
    ).toList
    all.size must equal(2)
    val s3 = all.find(_.projectId == libS3Project.id).get
    s3.dependsOn must be(Nil)
    s3.provides.map(_.identifier) must equal(Seq("io.flow.lib-s3"))

    val invoice = all.find(_.projectId == libInvoiceProject.id).get
    invoice.dependsOn.map(_.identifier) must equal(Seq("io.flow.lib-s3"))
    invoice.provides must be(Nil)
  }

  "getByOrganization" in {
    val resolution = projectDependencyResolutionService.getByOrganizationId(defaultOrg.id)
    resolution.resolved.toList match {
      case a :: b :: Nil =>
        a.position must be(1)
        a.projects.map(_.name) must equal(Seq("lib-s3"))
        b.position must be(2)
        b.projects.map(_.name) must equal(Seq("lib-invoice"))
      case _ => sys.error("Expected two entries")
    }
    resolution.circular must be(Nil)
  }
}
