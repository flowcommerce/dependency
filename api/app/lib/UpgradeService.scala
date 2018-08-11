package lib

import javax.inject.{Inject, Singleton}

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.google.inject.ImplementedBy
import db.{Authorization, LibrariesDao}
import io.flow.dependency.v0.models.{Library, Project}
import io.flow.lib.dependency.clients._
import io.flow.lib.dependency.git.Git
import io.flow.lib.dependency.upgrade.{BranchStrategy, DependenciesToUpgrade, Upgrader, UpgraderConfig}
import io.flow.lib.dependency.util.AsyncPager
import io.flow.util.Config
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

@ImplementedBy(classOf[UpgradeServiceImpl])
trait UpgradeService {
  def upgradeLibraries(): Future[Unit]
}


@Singleton class UpgradeServiceImpl @Inject()(librariesDao: LibrariesDao)
                                             (implicit git: Git,
                                              githubApi: GithubApi,
                                              dependencyApi: DependencyApi,
                                              ec: ExecutionContext,
                                              mat: Materializer, config: Config)
    extends UpgradeService {

  //filter out projects that can have upgrade PRs issued automatically.
  //This check is used after non-flow projects are filtered out (only Flow projects will be checked by this predicate)
  private val filterProjects: Project => Boolean = Hacks.isMetric

  private val DefaultPageSize = 100
  private val debugMode = false

  private val upgraderConfig = {
    val prefix = "io.flow.lib.dependency.upgrade.upgrader"

    val branchStrategy = {
      val configValue = config.requiredString(s"$prefix.branch-strategy")
      BranchStrategy
        .fromStringOption(configValue)
        .getOrElse(sys.error(s"Invalid branch strategy: $configValue"))
    }

    UpgraderConfig(
      blacklistProjects = config.requiredList(s"$prefix.blacklist-projects"),
      blacklistLibraries = config.requiredList(s"$prefix.blacklist-libraries"),
      blacklistBinaries = config.requiredList(s"$prefix.blacklist-binaries"),
      blacklistApibuilderUpdateProjects = config.requiredList(s"$prefix.blacklist-apibuilder-update-projects"),
      blacklistProjectLibraries = config.requiredMap(s"$prefix.blacklist-project-libraries"),
      branchStrategy = branchStrategy
    )
  }

  private val upgrader = new Upgrader(upgraderConfig)

  private def logInfo(msg: String): Unit = Logger.info(msg)

  private val streamLibraries: Source[Library, NotUsed] = {
    def librariesByOffset(offset: Int): Future[Seq[Library]] = Future {
      librariesDao.findAll(
        auth = Authorization.All,
        groupId = Some(Hacks.flowGroupId),
        limit = DefaultPageSize,
        offset = offset
      )
    }

    AsyncPager.create(librariesByOffset)
  }

  override val upgradeLibraries: Future[Unit] = {
    streamLibraries
      .scanAsync(Set.empty[Project]) { (projectsToSkip, library) =>
        upgradeDependent(library, projectsToSkip)
          .map(_ ++ projectsToSkip)
      }.runWith(Sink.ignore).map(_ => ())
  }

  private def upgradeDependent(library: Library, projectsToSkip: Set[Project]): Future[Set[Project]] = {
    def logUpgrading(): Unit = logInfo(s"Attempting upgrade of [$library]")

    val dependentsF = dependencyApi.getLibraryDependants(library.id)
      .map(_.toSet -- projectsToSkip)
      .map(_.toList)

    val dependentsStream: Source[Project, NotUsed] =
      Source.fromFuture(dependentsF)
        .mapConcat(identity)
        .filter(_.organization.key == Hacks.flowOrgKey)
        .filter(filterProjects)

    //contains all the projects in `dependentsStream`, not just the ones that were upgraded
    val upgradedProjectsStream = dependentsStream.mapAsync(parallelism = 1) { project =>
      upgradeProject(project)
        .map {
          case true  => logInfo(s"Upgraded project [$project]")
          case false => ()
        }.map(_ => project)
    }

    logUpgrading()
    upgradedProjectsStream.map(Set(_)).runFold(Set.empty[Project])(_ ++ _)
  }

  private def upgradeProject(project: Project): Future[Boolean] = {
    val projects = List(project)

    upgrader.doUpgrade(
      script = None,
      projects = projects,
      dependenciesToUpgrade = DependenciesToUpgrade.All,
      debug = debugMode
    ).map(_.nonEmpty)
  }
}
