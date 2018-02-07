package util

import java.util.UUID

import io.flow.dependency.v0.models.{OrganizationSummary, ProjectSummary, ResolverSummary, Visibility}

trait Factories {

  def makeName(): String = {
    s"Z Test ${UUID.randomUUID.toString}"
  }

  def makeKey(): String = {
    s"z-test-${UUID.randomUUID.toString.toLowerCase}"
  }

  def makeOrganizationSummary(
    id: String = UUID.randomUUID.toString,
    key: String = makeKey()
  ) = OrganizationSummary(
    id = id,
    key = key
  )

  def makeProjectSummary(
    id: String = UUID.randomUUID.toString,
    org: OrganizationSummary = makeOrganizationSummary(),
    name: String = makeName
  ) = ProjectSummary(
    id = id,
    organization = org,
    name = name
  )

  def makeResolverSummary(
    id: String = UUID.randomUUID.toString,
    org: OrganizationSummary = makeOrganizationSummary(),
    name: String = makeName
  ) = ResolverSummary(
    id = id,
    organization = Some(org),
    visibility = Visibility.Private,
    uri = "http://" + makeKey() + ".test.flow.io"
  )

}
