package services

import com.google.inject.ImplementedBy
import db.{Authorization, LibrariesDao, ProjectLibrariesDao, ProjectsDao}
import io.flow.dependency.v0.models.ProjectDependencyResolution
import javax.inject.Inject

@ImplementedBy(classOf[ProjectDependencyResolutionServiceImpl])
trait ProjectDependencyResolutionService {
  def getByOrganizationId(organizationId: String): ProjectDependencyResolution
}

class ProjectDependencyResolutionServiceImpl @Inject() (
  projectsDao: ProjectsDao,
  projectLibrariesDao: ProjectLibrariesDao,
  librariesDao: LibrariesDao,
) {

  override def getByOrganizationId(organizationId: String): ProjectDependencyResolution = {
    val projects = buildProjectInfo(organizationId)

  }

  def buildProjectInfo(organizationId: String) = {
    val projects = projectsDao.findAll(
      Authorization.All,
      organizationId = Some(organizationId),
      limit = None,
    )
  }

}
