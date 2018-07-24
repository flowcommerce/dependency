package lib

import javax.inject.{Inject, Singleton}

import cats.effect.IO
import com.google.inject.{AbstractModule, Provider, TypeLiteral}
import io.flow.lib.dependency.clients.{ApiClientGithubApi, DependencyApi, GithubApi, GithubConfig}
import io.flow.lib.dependency.git.Git
import io.flow.play.util.Config
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

class UpgradeModule extends AbstractModule {
  override def configure(): Unit = {
    bind(new TypeLiteral[DependencyApi[IO]]() {})
      .to(classOf[DaoBasedDependencyApi])

    bind(classOf[GithubConfig])
      .to(classOf[ConfiguredGithubConfig])

    bind(new TypeLiteral[GithubApi[IO]]() {})
      .toProvider(classOf[ConfiguredGithubApiProvider])

    bind(new TypeLiteral[Git[IO]](){})
      .toProvider(classOf[GitProvider])
  }
}

@Singleton
class ConfiguredGithubConfig @Inject()(config: Config)
  extends GithubConfig(config.requiredString("github.dependency.user.token"))

@Singleton
class ConfiguredGithubApiProvider @Inject()(wsClient: WSClient)(implicit githubConfig: GithubConfig, ec: ExecutionContext)
  extends Provider[GithubApi[IO]] {
  private val cached: ApiClientGithubApi[IO] = ApiClientGithubApi[IO](wsClient)

  override def get(): GithubApi[IO] = cached
}

@Singleton
class GitProvider @Inject()(implicit githubConfig: GithubConfig) extends Provider[Git[IO]] {
  private val cached: Git[IO] = Git.default[IO].unsafeRunSync()

  override def get(): Git[IO] = cached
}
