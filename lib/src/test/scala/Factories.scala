package com.bryzek.dependency.lib

import com.bryzek.dependency.v0.models.{BinarySummary, BinaryType, ItemSummary, ItemSummaryUndefinedType, LibrarySummary, OrganizationSummary, ProjectSummary, Reference}
import com.bryzek.dependency.v0.models.{ProjectDetail, Recommendation, RecommendationType}
import io.flow.play.util.{IdGenerator, Random}
import org.joda.time.DateTime

trait Factories {

  val idGenerator = IdGenerator("tst")
  val random = Random()

  def makeName(): String = {
    s"Z Test ${random.alpha(20)}"
  }

  def makeKey(): String = {
    "z-test-${random.alphaNumeric(20)}"
  }

  def makeRecommendation(
    `type`: RecommendationType = RecommendationType.Library
  ) = Recommendation(
    id = idGenerator.randomId(),
    project = ProjectDetail(
      id = idGenerator.randomId(),
      organization = makeOrganizationSummary(),
      name = makeName()
    ),
    `type` = `type`,
    `object` = Reference(idGenerator.randomId()),
    name = "io.flow.lib-play",
    from = "0.0.1",
    to = "0.0.1",
    createdAt = new DateTime()
  )

  def makeBinarySummary(
    id: String = idGenerator.randomId(),
    `type`: BinaryType = BinaryType.Scala
  ) = BinarySummary(
    id = id,
    organization = makeOrganizationSummary(),
    name = `type`
  )

  def makeLibrarySummary(
    id: String = idGenerator.randomId(),
    groupId: String = "io.flow",
    artifactId: String = "lib-play"
  ) = LibrarySummary(
    id = id,
    organization = makeOrganizationSummary(),
    groupId = groupId,
    artifactId = artifactId
  )

  def makeProjectSummary(
    id: String = idGenerator.randomId(),
    name: String = makeName()
  ) = ProjectSummary(
    id = id,
    organization = makeOrganizationSummary(),
    name = name
  )

  def makeOrganizationSummary(
    id: String = idGenerator.randomId(),
    key: String = makeKey()
  ) = OrganizationSummary(
    id = id,
    key = key
  )

}
