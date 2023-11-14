package cache

import db.{Authorization, ProjectsDao}
import io.flow.dependency.v0.models.Project
import io.flow.util.CacheWithFallbackToStaleData

@javax.inject.Singleton
case class ProjectsCache @javax.inject.Inject() (
  projectsDao: ProjectsDao,
) extends CacheWithFallbackToStaleData[String, Option[Project]] {

  override def refresh(projectId: String): Option[Project] = {
    projectsDao.findById(Authorization.All, projectId)
  }

  def findByProjectId(projectId: String): Option[Project] = {
    get(projectId)
  }
}
