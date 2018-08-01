package lib

import javax.inject.{Inject, Singleton}
import com.google.inject.Provider
import io.flow.lib.dependency.clients.{ApiClientGithubApi, DependencyApi, GithubApi, GithubConfig}
import io.flow.lib.dependency.git.Git
import io.flow.util.Config
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

class UpgradeModule extends Module {

  def bindings(env: Environment, conf: Configuration): Seq[Binding[_]] = List(
    bind[DependencyApi].to[DaoBasedDependencyApi],
    bind[GithubConfig].to[ConfiguredGithubConfig],
    bind[GithubApi].toProvider[ConfiguredGithubApiProvider],
    bind[Git].toProvider[GitProvider]
  )
}


@Singleton
class ConfiguredGithubConfig @Inject()(config: Config)
  extends GithubConfig(config.requiredString("github.dependency.user.token"))

@Singleton
class ConfiguredGithubApiProvider @Inject()(wsClient: WSClient)
                                           (implicit githubConfig: GithubConfig, ec: ExecutionContext)
  extends Provider[GithubApi] {
  override val get: GithubApi = ApiClientGithubApi(wsClient)
}

@Singleton
class GitProvider @Inject()(implicit githubConfig: GithubConfig, ec: ExecutionContext) extends Provider[Git] {
  override val get: Git = Git.default
}
