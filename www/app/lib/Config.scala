package com.bryzek.dependency.www.lib

import com.bryzek.dependency.v0.models.{BinarySummary, ItemSummary, ItemSummaryUndefinedType, LibrarySummary, ProjectSummary, Recommendation, RecommendationType, Scms}
import io.flow.play.util.{Config => FlowConfig}
import java.net.URLEncoder

object Config {

  private[this] lazy val config = play.api.Play.current.injector.instanceOf[FlowConfig]

  lazy val githubClientId = config.requiredString("github.dependency.client.id")
  lazy val dependencyWwwHost = config.requiredString("dependency.www.host")
  lazy val githubBaseUrl = s"$dependencyWwwHost/login/github"

  val VersionsPerPage = 5

  private val GithubScopes = Seq("user:email", "repo", "read:repo_hook", "write:repo_hook")

  private[this] val GitHubOauthUrl = "https://github.com/login/oauth/authorize"

  def githubOauthUrl(returnUrl: Option[String]): String = {
    GitHubOauthUrl + "?" + Seq(
      Some("scope" -> GithubScopes.mkString(",")),
      Some("client_id" -> githubClientId),
      returnUrl.map { url => ("redirect_uri" -> (s"$githubBaseUrl?return_url=" + URLEncoder.encode(url, "UTF-8"))) }
    ).flatten.map { case (key, value) =>
        s"$key=" + URLEncoder.encode(value, "UTF-8")
    }.mkString("&")

  }

  /**
    * Returns full URL to the file with the specified path
    */
  def scmsUrl(scms: Scms, uri: String, path: String): String = {
    val join = if (path.startsWith("/")) {
      ""
    } else {
      "/"
    }

    scms match {
      case Scms.Github => {
        uri + Seq("/blob/master", path).mkString(join)
      }
      case Scms.UNDEFINED(_) => {
        Seq(uri, path).mkString(join)
      }
    }
  }

}

