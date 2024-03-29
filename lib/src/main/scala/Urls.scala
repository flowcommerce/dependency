package io.flow.dependency.lib

import io.flow.dependency.v0.models.{
  BinarySummary,
  ItemSummary,
  ItemSummaryUndefinedType,
  LibrarySummary,
  OrganizationSummary,
  ProjectSummary,
  Recommendation,
  RecommendationType,
}
import io.flow.util.Config

/** All our URLs to the webapp go here. We tried to use the www routers directly as a separate project in the build, but
  * caused problems in the compile step (every other compile step failed). Instead we provide hard coded urls - but keep
  * in one file for easier maintenance.
  */
case class Urls(
  config: Config,
) {

  val github = "https://github.com/flowcommerce/dependency"

  lazy val wwwHost: String = config.requiredString("dependency.www.host")

  def binary(id: String) = s"/binaries/$id"
  def library(id: String) = s"/libraries/$id"
  def project(id: String) = s"/projects/$id"

  def subscriptions(userIdentifier: Option[String]): String = {
    val base = "/subscriptions/"
    userIdentifier match {
      case None => base
      case Some(id) => {
        val encoded = play.utils.UriEncoding.encodePathSegment(id, "UTF-8")
        s"$base$encoded"
      }
    }
  }

  def www(rest: play.api.mvc.Call): String = {
    www(rest.toString)
  }

  def www(rest: String): String = {
    wwwHost + rest
  }

  def recommendation(recommendation: Recommendation): String = {
    recommendation.`type` match {
      case RecommendationType.Library => library(recommendation.`object`.id)
      case RecommendationType.Binary => binary(recommendation.`object`.id)
      case RecommendationType.UNDEFINED(_) => "#"
    }
  }

  def itemSummary(summary: ItemSummary): String = {
    summary match {
      case BinarySummary(id, _, _) => binary(id)
      case LibrarySummary(id, _, _, _) => library(id)
      case ProjectSummary(id, _, _) => project(id)
      case ItemSummaryUndefinedType(_) => "#"
    }
  }

  def organization(organization: OrganizationSummary): String =
    s"/organizations/${organization.key}"

}
