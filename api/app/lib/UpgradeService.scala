package lib

import javax.inject.{Inject, Singleton}

import cats.effect.{Async, IO}
import cats.implicits._
import com.google.inject.ImplementedBy
import db.{Authorization, LibrariesDao}
import io.flow.dependency.v0.models.{Library, Project}
import io.flow.lib.dependency.clients._
import io.flow.lib.dependency.git.Git
import io.flow.lib.dependency.upgrade.{BranchStrategy, DependenciesToUpgrade, Upgrader, UpgraderConfig}
import io.flow.lib.dependency.util.AsyncPager
import play.api.Logger

import scala.language.higherKinds

@ImplementedBy(classOf[UpgradeServiceImpl])
trait UpgradeService {
  val upgradeLibraries: IO[Unit]
}


@Singleton class UpgradeServiceImpl @Inject()(librariesDao: LibrariesDao)
                                             (implicit git: Git[IO],
                                              githubApi: GithubApi[IO],
                                              dependencyApi: DependencyApi[IO])
    extends UpgradeService {

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

  private def logInfo(msg: String): IO[Unit] = IO { Logger.info(msg) }

  private val streamLibraries: fs2.Stream[IO, Library] = {
    def librariesByOffset(offset: Int): IO[Seq[Library]] = IO {
      librariesDao.findAll(
        auth = Authorization.All,
        groupId = Some(Hacks.flowGroupId),
        limit = DefaultPageSize,
        offset = offset
      )
    }

    AsyncPager[IO].create(librariesByOffset)
  }

  override val upgradeLibraries: IO[Unit] = {
    streamLibraries
      .evalScan(Set.empty[Project]) { (projectsToSkip, library) =>
        upgradeDependent(library, projectsToSkip)
          .map(_ ++ projectsToSkip)
      }
      .compile
      .drain
  }

  private def upgradeDependent(library: Library, projectsToSkip: Set[Project]): IO[Set[Project]] = {
    val logUpgrading = logInfo(s"Attempting upgrade of [$library]")

    val dependentsF = dependencyApi.getLibraryDependants(library.id)
      .map(_.toSet -- projectsToSkip)
      .map(_.toList)

    val dependentsStream = fs2.Stream
      .eval(dependentsF)
      .flatMap(fs2.Stream.emits(_))
      .filter(_.organization.key == Hacks.flowOrgKey)

    //contains all the projects in `dependentsStream`, not just the ones that were upgraded
    val upgradedProjectsStream = dependentsStream.evalMap { project =>
      upgradeProject(project)
        .flatMap {
          case true  => logInfo(s"Upgraded project [$project]")
          case false => IO.unit
        }
        .as(project)
    }

    logUpgrading *> upgradedProjectsStream.map(Set(_)).compile.foldMonoid
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
