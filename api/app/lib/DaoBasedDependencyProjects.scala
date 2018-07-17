package lib

import javax.inject.{Inject, Singleton}

import db.{LibrariesDao, ProjectLibrariesDao, ProjectsDao, RecommendationsDao}
import io.flow.dependency.v0.models.{Library, Project, Recommendation}
import io.flow.lib.dependency.clients.DependencyProjects
import io.flow.postgresql.Pager
import io.flow.util.Constants

@Singleton
class DaoBasedDependencyProjects @Inject()(librariesDao: LibrariesDao,
                                           projectsDao: ProjectsDao,
                                           projectLibrariesDao: ProjectLibrariesDao,
                                           recommendationsDao: RecommendationsDao,
                                           syncsService: SyncsService) extends DependencyProjects {

  override def getLibrary(libraryName: String): Option[Library] = {
    librariesDao.findAll(db.Authorization.All, artifactId = Some(libraryName), limit = 1).headOption
  }

  override def getLibraryDependants(libraryId: String): Seq[Project] = {
    val projectIds: Seq[String] = Pager.create[String] { offset =>
      projectLibrariesDao.findAll(db.Authorization.All, offset = offset, limit = Some(100), libraryId = Some(libraryId)).map(_.project.id)
    }.toSeq

    projectIds.grouped(100).flatMap { ids =>
      projectsDao.findAll(db.Authorization.All, ids = Some(ids), limit = projectIds.size)
    }.toSeq
  }

  override def recommendedUpgrades(): Seq[Project] = {
    Pager.create[Recommendation] { offset =>
      recommendationsDao.findAll(db.Authorization.All, offset = offset, limit = 100)
    }.grouped(100).flatMap { recommendations =>
      projectsDao.findAll(db.Authorization.All, ids = Some(recommendations.map(_.project.id)), limit = recommendations.size)
    }.toSeq.distinct.sortBy(_.name)
  }
  override def getRecommendationsForProject(project: Project): Seq[Recommendation] = {
    recommendationsDao.findAll(db.Authorization.All, projectId = Some(project.id), limit = 100)
  }

  override def getProjects(): Seq[Project] = {
    Pager.byOffset { offset =>
      projectsDao.findAll(
        db.Authorization.All,
        offset = offset, limit = 100
      )
    }.toSeq
  }

  override def syncLibrary(libraryName: String): Unit = {
    val libraryId = librariesDao
      .findAll(
        auth = db.Authorization.All,
        id = None,
        artifactId = Some(libraryName),
        limit = 1
      )
      .map(_.id).headOption
      .getOrElse(throw new Exception(s"Library with name $libraryName not found"))

    syncsService.syncLibrary(libraryId, Constants.SystemUser)
  }
}
