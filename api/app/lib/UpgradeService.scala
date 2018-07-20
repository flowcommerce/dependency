package lib

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import io.flow.dependency.v0.models.{Library, Project}
import io.flow.lib.dependency.clients.{DependencyProjects, GithubClient, GithubClientBuilder}
import io.flow.lib.dependency.upgrade.{BranchStrategy, DependenciesToUpgrade, Upgrader, UpgraderConfig}
import io.flow.play.util.Config
import play.api.Logger
import play.api.libs.ws.WSClient
import cats.effect.IO
import cats.implicits._

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[UpgradeServiceImpl])
trait UpgradeService {
  def upgradeDependent(library: String): IO[Option[Library]]
}

@Singleton class UpgradeServiceImpl @Inject()(
    ws: WSClient,
    config: Config,
    dependencyProjects: DaoBasedDependencyProjects)(implicit ec: ExecutionContext)
    extends UpgradeService {
  val logger = Logger(getClass)

  private val debugMode = false

  private val githubToken =
    config.requiredString("github.dependency.user.token")

  private val githubClient: GithubClient =
    new GithubClientBuilder(ws).build(githubToken)

  //todo move to conf file
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

  def logInfo(msg: String) = IO { logger.info(msg) }

  override def upgradeDependent(name: String): IO[Option[Library]] = dependencyProjects.getLibrary(name).flatMap {
    _.traverse { library =>

      val logUpgrading = logInfo(s"Attempting upgrade of [$library]")

      val dependantsF = dependencyProjects.getLibraryDependants(library.id)

      val upgradeDependants = dependantsF.flatMap { dependants =>
        val doUpgrades = dependants.traverse_ { project =>
          upgradeProject(project).flatMap {
            case true  => logInfo(s"Upgraded project [$project]")
            case false => IO.unit
          }
        }

        val logNoDependants = if(dependants.isEmpty)
          logInfo(s"No dependants of $name found")
        else IO.unit

        doUpgrades *> logNoDependants
      }

      logUpgrading *> upgradeDependants.as(library)
    }
  }

  private def upgradeProject(project: Project): IO[Boolean] = {
    val projects = List(project)

    upgrader.doUpgrade(
      script = None,
      projects = projects,
      dependenciesToUpgrade = DependenciesToUpgrade.All,
      debug = debugMode
    ).map(_.nonEmpty)
  }
}
