package controllers

import io.flow.dependency.v0.models.json._
import db.{Authorization, InternalTasksDao, LibrariesDao, ProjectsDao}
import io.flow.log.RollbarLogger
import io.flow.postgresql.Pager
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class GithubWebhooks @javax.inject.Inject() (
  val controllerComponents: ControllerComponents,
  projectsDao: ProjectsDao,
  librariesDao: LibrariesDao,
  internalTasksDao: InternalTasksDao,
  logger: RollbarLogger
) extends BaseController {

  def postByProjectId(projectId: String) = Action {
    projectsDao.findById(Authorization.All, projectId) match {
      case None => {
        NotFound
      }
      case Some(project) => {
        logger
          .withKeyValue("project", Json.toJson(project))
          .info("Received github webook for project - triggering sync")
        internalTasksDao.queueProject(project)

        // Find any libaries with the exact name of this project and
        // opportunistically trigger a sync of that library
        Pager.create { offset =>
          librariesDao.findAll(
            Authorization.All,
            artifactId = Some(project.name),
            offset = offset
          )
        }.foreach { library =>
          // TODO: Queue into the future to leave time for
          // the artifact to be published
          internalTasksDao.queueLibrary(library)
        }

        Ok(Json.toJson(Map("result" -> "success")))
      }
    }
  }

}
