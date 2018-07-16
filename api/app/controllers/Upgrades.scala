package controllers

import javax.inject.{Inject, Singleton}

import db.{Authorization, ProjectsDao, TokensDao}
import io.flow.lib.dependency.clients.{
  DependencyProjects,
  GithubClient,
  GithubClientBuilder
}
import io.flow.lib.dependency.upgrade.DependenciesToUpgrade.All
import io.flow.lib.dependency.upgrade.{Upgrader, UpgraderConfig}
import io.flow.play.controllers.InjectedFlowController
import io.flow.play.util.Config
import play.api.libs.ws.WSClient

@Singleton
class Upgrades @Inject()(dependencyProjects: DependencyProjects,
                         ws: WSClient,
                         projectsDao: ProjectsDao,
                         tokensDao: TokensDao,
                         config: Config)
    extends InjectedFlowController {

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

  def postProjectById(id: String) = Anonymous {
    val project = projectsDao.findById(Authorization.All, id)
    upgrader.doUpgrade(None, project.toList, All, debug = false)

    Ok(s"Upgraded project ${project.map(_.name)}")
  }
}
