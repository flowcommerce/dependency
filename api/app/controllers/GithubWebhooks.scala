package controllers

import io.flow.dependency.actors.MainActor
import io.flow.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import db.{Authorization, LibrariesDao, ProjectsDao}
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.{Config, Validation}
import io.flow.postgresql.Pager
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class GithubWebhooks @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  projectsDao: ProjectsDao,
  librariesDao: LibrariesDao,
  @javax.inject.Named("main-actor") mainActor: akka.actor.ActorRef
) extends FlowController  {

  def postByProjectId(projectId: String) = Action { request =>
    projectsDao.findById(Authorization.All, projectId) match {
      case None => {
        NotFound
      }
      case Some(project) => {
        play.api.Logger.info(s"Received github webook for project[${project.id}] name[${project.name}]")
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
