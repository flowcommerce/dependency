package lib

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import io.flow.dependency.v0.models.Project
import io.flow.lib.dependency.clients.{
  DependencyProjects,
  GithubClient,
  GithubClientBuilder
}
import io.flow.lib.dependency.upgrade.{
  DependenciesToUpgrade,
  Upgrader,
  UpgraderConfig
}
import io.flow.play.util.Config
import play.api.libs.ws.WSClient

@ImplementedBy(classOf[UpgradeServiceImpl])
trait UpgradeService {
  final def upgradeLibrary(library: Project): Unit = {
    getDependentProjects(library.name)
      .foreach(upgradeProject)
  }

  protected def upgradeProject(project: Project): Unit

  protected def getDependentProjects(name: String): Seq[Project]
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

  //todo make blacklists configurable? Hardcode in hacks?
  private val upgraderConfig = UpgraderConfig(
    blacklistProjects = Nil,
    blacklistLibraries = Nil,
    blacklistBinaries = Nil,
    blacklistApibuilderUpdateProjects = Nil,
    blacklistProjectLibraries = Map.empty
  )

  private val upgrader =
    new Upgrader(dependencyProjects, githubClient, upgraderConfig)

  override protected def getDependentProjects(name: String): Seq[Project] =
    dependencyProjects.getDependentProjects(name)

  override def upgradeProject(project: Project): Unit = {
    val projects = List(project)
    upgrader.doUpgrade(None, projects, DependenciesToUpgrade.All, debug = true)
  }
}
