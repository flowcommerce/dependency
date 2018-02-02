package io.flow.dependency.api.lib

import db.{Authorization, ResolversDao}
import io.flow.dependency.v0.models.{OrganizationSummary, ResolverSummary, Resolver, Visibility}

case class ArtifactResolution(
  resolver: ResolverSummary,
  versions: Seq[ArtifactVersion]
) {
  assert(!versions.isEmpty, "Must have at least one version")
}

trait LibraryArtifactProvider {

  /**
    * Returns the artifacts for this library.
    * 
    * @param organization Used to look up private resolvers for this organization.
    * @param resolver If specified, we search this resolver first
    */
  def resolve(
    organization: OrganizationSummary,
    groupId: String,
    artifactId: String
  ): Option[ArtifactResolution]

  /**
    * Attempts to resolve a library at the specified resolver. A
    * return value of None indicates we did NOT find this
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
      credentials = ResolversDao.credentials(resolver)
    ) match {
      case Nil => None
      case versions => Some(ArtifactResolution(ResolversDao.toSummary(resolver), versions))
    }
  }

}


case class DefaultLibraryArtifactProvider() extends LibraryArtifactProvider {

  override def resolve(
    organization: OrganizationSummary,
    groupId: String,
    artifactId: String
  ): Option[ArtifactResolution] = {
    internalResolve(
      organization = organization,
      groupId = groupId,
      artifactId = artifactId,
      limit = 100,
      offset = 0
    )
  }

  private[this] def internalResolve(
    organization: OrganizationSummary,
    groupId: String,
    artifactId: String,
    limit: Long,
    offset: Long
  ): Option[ArtifactResolution] = {
    ResolversDao.findAll(
      Authorization.Organization(organization.id),
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
            credentials = ResolversDao.credentials(resolver)
          ) match {
            case Nil => {}
            case versions => {
              return Some(
                ArtifactResolution(
                  ResolverSummary(
                    id = resolver.id,
                    organization = resolver.visibility match {
                      case Visibility.Public => None
                      case Visibility.Private | Visibility.UNDEFINED(_) => Some(organization)
                    },
                    visibility = resolver.visibility,
                    uri = resolver.uri
                  ),
                  versions
                )
              )
            }
          }
        }

        internalResolve(
          organization = organization,
          groupId = groupId,
          artifactId = artifactId,
          limit = limit,
          offset = offset + limit
        )
      }
    }
  }

}
