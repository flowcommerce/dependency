package io.flow.dependency.www.lib

import io.flow.dependency.v0.models.Scms
import io.flow.play.util.{Config => FlowConfig}
import java.net.URLEncoder
import javax.inject.{Inject, Singleton}

@Singleton
class GitHubConfig @Inject() (config: FlowConfig) {
  private val Scopes = Seq("user:email", "repo", "read:repo_hook", "write:repo_hook")
  private val OauthUrl = "https://github.com/login/oauth/authorize"

  private val githubClientId = config.requiredString("github.dependency.client.id")
  private val dependencyWwwHost = config.requiredString("dependency.www.host")
  private val githubBaseUrl = s"$dependencyWwwHost/login/github"

  def githubOauthUrl(returnUrl: Option[String]): String = {
    val returnUrlParam = returnUrl
      .map { encoded =>
        s"$githubBaseUrl?return_url=$encoded"
      }

    val params: Map[String, String] = Seq(
      Some("scope" -> Scopes.mkString(",")),
      Some("client_id" -> githubClientId),
      returnUrlParam.map("redirect_uri" -> _),
    ).flatten.toMap

    val queryParams =
      params.view
        .mapValues(URLEncoder.encode(_, "UTF-8"))
        .map { case (key, value) =>
          s"$key=$value"
        }

    OauthUrl + "?" + queryParams.mkString("&")
  }

}

object Config {
  val VersionsPerPage = 5

  /** Returns full URL to the file with the specified path
    */
  def scmsUrl(scms: Scms, uri: String, path: String): String = {
    val separator = if (path.startsWith("/")) "" else "/"
    val pathSep = separator + path

    scms match {
      case Scms.Github =>
        s"$uri/blob/main$pathSep"

      case Scms.UNDEFINED(_) =>
        uri + pathSep
    }
  }

}
