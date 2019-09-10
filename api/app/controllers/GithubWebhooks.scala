package controllers

import io.flow.dependency.actors.MainActor
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
  logger: RollbarLogger,
  @javax.inject.Named("main-actor") mainActor: akka.actor.ActorRef
) extends BaseController {

  def postByProjectId(projectId: String) = Action {
    projectsDao.findById(Authorization.All, projectId) match {
      case None => {
        NotFound
      }
      case Some(project) => {
        logger.withKeyValue("project", Json.toJson(project)).info(s"Received github webook for project")
        mainActor ! MainActor.Messages.ProjectSync(project.id)

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
          internalTasksDao.createSyncIfNotQueued(library)
        }

        Ok(Json.toJson(Map("result" -> "success")))
      }
    }
  }

}
