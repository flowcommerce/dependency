package services

import util.DependencySpec

class ProjectDependencyResolutionServiceSpec extends DependencySpec {

  def projectDependencyResolutionService: ProjectDependencyResolutionServiceImpl = init[ProjectDependencyResolutionService].asInstanceOf[ProjectDependencyResolutionServiceImpl]

  "buildProjectInfo for no project" in {
    projectDependencyResolutionService.buildProjectInfo(Nil) must be(Nil)
  }

  "buildProjectInfo 'depends' and 'provides'" in {
    val org = createOrganization()
    val libS3Project = createProject(org)(
      createProjectForm(org, name = "lib-s3")
    )
    upsertLibrary(groupId = "io.flow", artifactId = "lib-s3")

    val libInvoiceProject = createProject(org)(
      createProjectForm(org, name = "lib-invoice")
    )

    createProjectLibrary(libInvoiceProject)(
      createProjectLibraryForm(libInvoiceProject).copy(
        groupId = "io.flow",
        artifactId = "lib-s3",
      )
    )

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
}
