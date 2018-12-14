package controllers

import io.flow.dependency.actors.MainActor
import io.flow.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import db.{Authorization, LibrariesDao, ProjectsDao}
import io.flow.log.RollbarLogger
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.{Config, Validation}
import io.flow.postgresql.Pager
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class GithubWebhooks @javax.inject.Inject() (
  val controllerComponents: ControllerComponents,
  projectsDao: ProjectsDao,
  librariesDao: LibrariesDao,
  logger: RollbarLogger,
  @javax.inject.Named("main-actor") mainActor: akka.actor.ActorRef
) extends BaseController {

  def postByProjectId(projectId: String) = Action { request =>
    projectsDao.findById(Authorization.All, projectId) match {
      case None => {
        NotFound
      }
      case Some(project) => {
        logger.withKeyValue("project", Json.toJson(project)).info(s"Received github webook for project")
        mainActor ! MainActor.Messages.ProjectSync(project.id)

        // Find any libaries with the exact name of this project and
        // opportunistically trigger a sync of that library a few
        // times into the future. This supports the normal workflow of
        // tagging a repository and then publishing a new version of
        // that artifact. We want to pick up that new version
        // reasonably quickly.
        Pager.create { offset =>
          librariesDao.findAll(
            Authorization.All,
            artifactId = Some(project.name),
            offset = offset
          )
        }.foreach { library =>
          Seq(30, 60, 120, 180).foreach { seconds =>
            mainActor ! MainActor.Messages.LibrarySyncFuture(library.id, seconds)
          }
        }

        Ok(Json.toJson(Map("result" -> "success")))
      }
    }
  }

}
