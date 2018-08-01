package lib

import javax.inject.{Inject, Singleton}
import cats.implicits._
import db._
import io.flow.dependency.v0.models.{Library, Project, Recommendation}
import io.flow.lib.dependency.clients.DependencyApi
import io.flow.postgresql.Pager
import io.flow.util.Constants

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

@Singleton
final class DaoBasedDependencyApi @Inject()(librariesDao: LibrariesDao,
                                            projectsDao: ProjectsDao,
                                            projectLibrariesDao: ProjectLibrariesDao,
                                            recommendationsDao: RecommendationsDao,
                                            syncsService: SyncsService)
                                           (implicit val ec: ExecutionContext) extends DependencyApi {
  private val DefaultPageSize = 100

  override def getAllLibraries(): Future[List[Library]] = Future {
    Pager.byOffset { offset =>
      librariesDao.findAll(auth = Authorization.All, offset = offset, limit = DefaultPageSize)
    }.toList
  }

  override def recommendedUpgrades(): Future[List[Project]] = Future {
    Pager.byOffset[Recommendation] { offset =>
      recommendationsDao.findAll(db.Authorization.All, offset = offset, limit = DefaultPageSize)
    }.map(_.project.id).grouped(DefaultPageSize).flatMap { projectIds =>
      projectsDao.findAll(db.Authorization.All, ids = Some(projectIds), limit = projectIds.size)
    }.toStream.distinct.sortBy(_.name).toList
  }

  override def getProjects(): Future[List[Project]] = Future {
    Pager.byOffset { offset =>
      projectsDao.findAll(
        db.Authorization.All,
        offset = offset, limit = DefaultPageSize
      )
    }.toList
  }

  override def getLibrary(libraryName: String): Future[Option[Library]] = Future {
    librariesDao.findAll(db.Authorization.All, artifactId = Some(libraryName), limit = 1).headOption
  }

  override def getLibraryDependants(libraryId: String): Future[List[Project]] = Future {
    val projectIds = Pager.byOffset { offset =>
      projectLibrariesDao.findAll(
        db.Authorization.All,
        offset = offset,
        limit = Some(DefaultPageSize),
        libraryId = Some(libraryId)
      )
    }.map(_.project.id)

    projectIds.grouped(DefaultPageSize).flatMap { ids =>
      projectsDao.findAll(db.Authorization.All, ids = Some(ids), limit = ids.size)
    }
  }.map(_.toList)

  override def getRecommendationsForProject(project: Project): Future[List[Recommendation]] = Future {
    recommendationsDao.findAll(db.Authorization.All, projectId = Some(project.id), limit = DefaultPageSize).toList
  }

  override def syncLibrary(libraryName: String): Future[Unit] = Future {
    val libraries = {
      librariesDao
        .findAll(
          auth = db.Authorization.All,
          id = None,
          artifactId = Some(libraryName),
          limit = 1
        )
    }

    val library = libraries
      .headOption.toRight[Throwable](new Exception(s"Library with name $libraryName not found"))
      .liftTo[Future]

    library.map {
      lib => syncsService.syncLibrary(lib.id, Constants.SystemUser)
    }
  }

  override def syncLibraries(groupId: String): Future[Unit] = Future {
    syncsService.syncLibraries(Some(groupId), Constants.SystemUser)
  }
}
