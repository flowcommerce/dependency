package lib

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import io.flow.dependency.v0.models.{Library, Project}
import io.flow.lib.dependency.clients.{DependencyProjects, GithubClient, GithubClientBuilder}
import io.flow.lib.dependency.upgrade.{BranchStrategy, DependenciesToUpgrade, Upgrader, UpgraderConfig}
import io.flow.play.util.Config
import play.api.libs.ws.WSClient

@ImplementedBy(classOf[UpgradeServiceImpl])
trait UpgradeService {
  def upgradeLibrary(library: String): Option[Library]
}

@Singleton class UpgradeServiceImpl @Inject()(
    ws: WSClient,
    config: Config,
    dependencyProjects: DependencyProjects)
    extends UpgradeService {
  private val debugMode = false

  private val githubToken =
    config.requiredString("github.dependency.user.token")

  private val githubClient: GithubClient =
    new GithubClientBuilder(ws).build(githubToken)

  //todo make blacklists configurable? Hardcode in hacks?
  private val upgraderConfig = UpgraderConfig(
    blacklistProjects = Nil,
    blacklistLibraries = Nil,
    blacklistBinaries = Nil,
    blacklistApibuilderUpdateProjects = Nil,
    blacklistProjectLibraries = Map.empty,
    branchStrategy = BranchStrategy.UseExisting
  )

  private val upgrader =
    new Upgrader(dependencyProjects, githubClient, upgraderConfig)

  override def upgradeLibrary(library: String): Option[Library] = {
    dependencyProjects.getLibrary(library).map { library =>
      dependencyProjects.getLibraryDependants(library.id).foreach(upgradeProject)
      library
    }
  }

  private def upgradeProject(project: Project): Unit = {
    val projects = List(project)

    upgrader.doUpgrade(
      script = None,
      projects = projects,
      dependenciesToUpgrade = DependenciesToUpgrade.All,
      debug = debugMode
    )
  }
}
