package controllers

import db.{Authorization, InternalTasksDao, ProjectsDao}
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class GithubWebhooks @javax.inject.Inject() (
  val controllerComponents: ControllerComponents,
  projectsDao: ProjectsDao,
  internalTasksDao: InternalTasksDao,
) extends BaseController {

  def postByProjectId(projectId: String): Action[AnyContent] = Action {
    projectsDao.findById(Authorization.All, projectId) match {
      case None => {
        NotFound
      }
      case Some(project) => {
        internalTasksDao.queueProject(project)
        Ok(Json.toJson(Map("result" -> "success")))
      }
    }
  }

}
