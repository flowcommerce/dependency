package io.flow.dependency.api.lib

import cache.OrganizationsCache
import db.{Authorization, ResolversDao}
import io.flow.dependency.v0.models.{Resolver, ResolverSummary, Visibility}
import javax.inject.Inject

case class ArtifactResolution(
  resolver: ResolverSummary,
  versions: Seq[ArtifactVersion]
) {
  assert(versions.nonEmpty, "Must have at least one version")
}

trait LibraryArtifactProvider {

  def resolversDao: ResolversDao

  /** Returns the artifacts for this library.
    *
    * @param organizationId
    *   Used to look up private resolvers for this organization.
    */
  def resolve(
    organizationId: String,
    groupId: String,
    artifactId: String
  ): Option[ArtifactResolution]

  /** Attempts to resolve a library at the specified resolver. A return value of None indicates we did NOT find this
    * groupId/artifactId on this resolver.
    */
  def resolve(
    resolver: Resolver,
    groupId: String,
    artifactId: String
  ): Option[ArtifactResolution] = {
    RemoteVersions.fetch(
      resolver = resolver.uri,
      groupId = groupId,
      artifactId = artifactId,
      credentials = resolversDao.credentials(resolver)
    ) match {
      case Nil => None
      case versions => Some(ArtifactResolution(resolversDao.toSummary(resolver), versions))
    }
  }

}

class DefaultLibraryArtifactProvider @Inject() (
  override val resolversDao: ResolversDao,
  organizationsCache: OrganizationsCache
) extends LibraryArtifactProvider {

  override def resolve(
    organizationId: String,
    groupId: String,
    artifactId: String
  ): Option[ArtifactResolution] = {
    internalResolve(
      organizationId = organizationId,
      groupId = groupId,
      artifactId = artifactId,
      limit = 100,
      offset = 0
    )
  }

  private[this] def internalResolve(
    organizationId: String,
    groupId: String,
    artifactId: String,
    limit: Long,
    offset: Long
  ): Option[ArtifactResolution] = {
    resolversDao.findAll(
      Authorization.Organization(organizationId),
      limit = limit,
      offset = offset
    ) match {
      case Nil => {
        None
      }
      case resolvers => {
        resolvers.foreach { resolver =>
          RemoteVersions.fetch(
            resolver = resolver.uri,
            groupId = groupId,
            artifactId = artifactId,
            credentials = resolversDao.credentials(resolver)
          ) match {
            case Nil => {}
            case versions => {
              return Some(ArtifactResolution(toResolverSummary(organizationId, resolver), versions))
            }
          }
        }

        internalResolve(
          organizationId = organizationId,
          groupId = groupId,
          artifactId = artifactId,
          limit = limit,
          offset = offset + limit
        )
      }
    }
  }

  private[this] def toResolverSummary(organizationId: String, resolver: Resolver): ResolverSummary = {
    ResolverSummary(
      id = resolver.id,
      organization = resolver.visibility match {
        case Visibility.Public => None
        case Visibility.Private | Visibility.UNDEFINED(_) =>
          organizationsCache.findSummaryByOrganizationId(organizationId)
      },
      visibility = resolver.visibility,
      uri = resolver.uri
    )
  }

}
