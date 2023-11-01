package cache

import db.{Authorization, OrganizationsDao}
import io.flow.dependency.v0.models.{Organization, OrganizationSummary}
import io.flow.util.CacheWithFallbackToStaleData

@javax.inject.Singleton
case class OrganizationsCache @javax.inject.Inject() (
  organizationsDao: OrganizationsDao
) extends CacheWithFallbackToStaleData[String, Option[Organization]] {

  override def refresh(organizationId: String): Option[Organization] = {
    organizationsDao.findById(Authorization.All, organizationId)
  }

  def findByOrganizationId(organizationId: String): Option[Organization] = {
    get(organizationId)
  }

  def findSummaryByOrganizationId(organizationId: String): Option[OrganizationSummary] = {
    findByOrganizationId(organizationId).map(toSummary)
  }

  def toSummary(org: Organization): OrganizationSummary = {
    OrganizationSummary(
      id = org.id,
      key = org.key
    )
  }
}
