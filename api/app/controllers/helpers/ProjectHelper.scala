package controllers.helpers

import javax.inject.{Inject, Singleton}

import db.{Authorization, ProjectsDao}
import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.models.Project
import play.api.mvc.{Result, Results}

@Singleton
class ProjectHelper @Inject() (
  projectsDao: ProjectsDao,
) {

  def withProject(user: UserReference, id: String)(
    f: Project => Result,
  ): Result = {
    projectsDao.findById(Authorization.User(user.id), id) match {
      case None => {
        Results.NotFound
      }
      case Some(project) => {
        f(project)
      }
    }
  }

}
