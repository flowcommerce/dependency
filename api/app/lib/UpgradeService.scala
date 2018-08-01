package lib

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import javax.inject.{Inject, Singleton}
import com.google.inject.ImplementedBy
import db.{Authorization, LibrariesDao}
import io.flow.dependency.v0.models.{Library, Project}
import io.flow.lib.dependency.clients._
import io.flow.lib.dependency.git.Git
import io.flow.lib.dependency.upgrade.{BranchStrategy, DependenciesToUpgrade, Upgrader, UpgraderConfig}
import io.flow.lib.dependency.util.AsyncPager
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
                                              mat: Materializer)
    extends UpgradeService {

  private val parallelism = 1
  private val DefaultPageSize = 100
  private val debugMode = false

  //todo move to conf file
  private val upgraderConfig = UpgraderConfig(
    blacklistProjects = Nil,
    blacklistLibraries = Nil,
    blacklistBinaries = Nil,
    blacklistApibuilderUpdateProjects = Nil,
    blacklistProjectLibraries = Map.empty,
    branchStrategy = BranchStrategy.UseExisting
  )

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

    val dependentsStream =
      Source.fromFuture(dependentsF)
        .mapConcat(identity)
        .filter(_.organization.key == Hacks.flowOrgKey)

    //contains all the projects in `dependentsStream`, not just the ones that were upgraded
    val upgradedProjectsStream = dependentsStream.mapAsync(parallelism) { project =>
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
