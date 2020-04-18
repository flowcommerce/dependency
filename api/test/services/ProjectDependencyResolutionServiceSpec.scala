package services

import util.DependencySpec

class ProjectDependencyResolutionServiceSpec extends DependencySpec {

  def projectDependencyResolutionService: ProjectDependencyResolutionServiceImpl = init[ProjectDependencyResolutionService].asInstanceOf[ProjectDependencyResolutionServiceImpl]

  "buildProjectInfo for no project" in {
    projectDependencyResolutionService.buildProjectInfo(Nil) must be(Nil)
  }

  "buildProjectInfo 'provides'" in {
    val org = createOrganization()
    val libS3Project = createProject(org)(
      createProjectForm(org, name = "lib-s3")
    )

    upsertLibrary(groupId = "io.flow", artifactId = "lib-s3")
    createLibrary(org) // some other library to ensure we do not select this one

    projectDependencyResolutionService.buildProjectInfo(
      Seq(libS3Project)
    ).toList match {
      case info :: Nil => {
        info.projectId must equal(libS3Project.id)
        info.dependsOn must be(Nil)
        info.provides.map(_.identifier) must equal(Seq("io.flow.lib-s3"))
      }
      case _ => sys.error("Expected 1 project info")
    }
  }


}
