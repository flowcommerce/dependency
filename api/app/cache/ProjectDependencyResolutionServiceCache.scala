package cache

import io.flow.dependency.v0.models.ProjectDependencyResolution
import io.flow.util.CacheWithFallbackToStaleData
import services.ProjectDependencyResolutionService

case class ProjectDependencyResolutionServiceCacheKey(
  organizationKey: String,
  groupId: String
)

@javax.inject.Singleton
case class ProjectDependencyResolutionServiceCache @javax.inject.Inject() (
  service: ProjectDependencyResolutionService
) extends CacheWithFallbackToStaleData[ProjectDependencyResolutionServiceCacheKey, ProjectDependencyResolution]
  with ProjectDependencyResolutionService {

  override def refresh(key: ProjectDependencyResolutionServiceCacheKey): ProjectDependencyResolution = {
    service.getByOrganizationKey(
      organizationKey = key.organizationKey,
      groupId = key.groupId
    )
  }

  override def getByOrganizationKey(organizationKey: String, groupId: String): ProjectDependencyResolution = {
    get(
      ProjectDependencyResolutionServiceCacheKey(
        organizationKey = organizationKey,
        groupId = groupId
      )
    )
  }

}
