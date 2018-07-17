package lib

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import io.flow.dependency.v0.models.Project
import io.flow.lib.dependency.clients.{
  DependencyProjects,
  GithubClient,
  GithubClientBuilder
}
import io.flow.lib.dependency.upgrade.ProjectUpgrader
import io.flow.play.util.Config
import play.api.libs.ws.WSClient
import io.flow.lib.dependency.implicits.project._

@ImplementedBy(classOf[UpgradeServiceImpl])
trait UpgradeService {
  def upgradeProject(project: Project): Unit
}
@Singleton class UpgradeServiceImpl @Inject()(
    ws: WSClient,
    config: Config,
    dependencyProjects: DependencyProjects)
    extends UpgradeService {

  private val githubToken =
    config.requiredString("github.dependency.user.token")

  private val githubClient: GithubClient =
    new GithubClientBuilder(ws).build(githubToken)

  override def upgradeProject(project: Project): Unit = {
    val recommendations =
      dependencyProjects.getRecommendationsForProject(project)

    new ProjectUpgrader(githubClient, recommendations, Nil, false)
      .upgrade(project.owner, project.repo)
  }
}
