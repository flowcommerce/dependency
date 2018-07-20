package lib

import javax.inject.{Inject, Singleton}

import cats.effect.IO
import db._
import io.flow.dependency.v0.models.{Library, Project, Recommendation}
import io.flow.lib.dependency.clients.{AsyncPager, DependencyProjects}
import io.flow.util.Constants
import cats.implicits._

import scala.language.higherKinds
import io.flow.lib.dependency.implicits.internal.StreamOps._

@Singleton
final class DaoBasedDependencyProjects @Inject()(librariesDao: LibrariesDao,
                                             projectsDao: ProjectsDao,
                                             projectLibrariesDao: ProjectLibrariesDao,
                                             recommendationsDao: RecommendationsDao,
                                             syncsService: SyncsService) extends DependencyProjects[IO] {
  private val DefaultPageSize = 100

  override val getAllLibraries: IO[List[Library]] = AsyncPager[IO].create { offset =>
    IO {
      librariesDao.findAll(auth = Authorization.All, offset = offset, limit = SyncsService.LibrariesToSync)
    }
  }.compile.toList

  override val recommendedUpgrades: IO[List[Project]] = {
    AsyncPager[IO].create[Recommendation] { offset =>
      IO {
        recommendationsDao.findAll(db.Authorization.All, offset = offset, limit = DefaultPageSize)
      }
    }.map(_.project.id).segmentN(DefaultPageSize).evalSegments { projectIds =>
      IO {
        projectsDao.findAll(db.Authorization.All, ids = Some(projectIds), limit = projectIds.size)
      }
    }
  }.compile.to[collection.immutable.Stream].map {
    _.distinct.sortBy(_.name).toList
  }

  override val getProjects: IO[List[Project]] = {
    AsyncPager[IO].create { offset =>
      IO {
        projectsDao.findAll(
          db.Authorization.All,
          offset = offset, limit = DefaultPageSize
        )
      }
    }
  }.compile.toList

  override def getLibrary(libraryName: String): IO[Option[Library]] = IO {
    librariesDao.findAll(db.Authorization.All, artifactId = Some(libraryName), limit = 1).headOption
  }

  override def getLibraryDependants(libraryId: String): IO[List[Project]] = {
    val projectIds = AsyncPager[IO].create { offset =>
      IO {
        projectLibrariesDao.findAll(
          db.Authorization.All,
          offset = offset,
          limit = Some(DefaultPageSize),
          libraryId = Some(libraryId)
        )
      }
    }.map(_.project.id)

    projectIds.segmentN(DefaultPageSize).evalSegments { ids =>
      IO {
        projectsDao.findAll(db.Authorization.All, ids = Some(ids), limit = ids.size)
      }
    }
  }.compile.toList

  override def getRecommendationsForProject(project: Project): IO[List[Recommendation]] = IO {
    recommendationsDao.findAll(db.Authorization.All, projectId = Some(project.id), limit = DefaultPageSize).toList
  }

  override def syncLibrary(libraryName: String): IO[Unit] = {
    val libraries = IO {
      librariesDao
        .findAll(
          auth = db.Authorization.All,
          id = None,
          artifactId = Some(libraryName),
          limit = 1
        )
    }

    val library = libraries
      .map(_.headOption.toRight[Throwable](new Exception(s"Library with name $libraryName not found")))
      .flatMap(IO.fromEither)

    library.flatMap {
      lib => IO {
        syncsService.syncLibrary(lib.id, Constants.SystemUser)
      }
    }
  }

  override def syncLibraries(groupId: String): IO[Unit] = IO {
    syncsService.syncLibraries(Some(groupId), Constants.SystemUser)
  }
}
