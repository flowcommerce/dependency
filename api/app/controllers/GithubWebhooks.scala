package controllers

import com.bryzek.dependency.actors.MainActor
import com.bryzek.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import db.{Authorization, LibrariesDao, ProjectsDao}
import io.flow.play.controllers.IdentifiedRestController
import io.flow.play.util.Validation
import io.flow.postgresql.Pager
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class GithubWebhooks @javax.inject.Inject() (
  override val config: io.flow.play.util.Config,
  override val tokenClient: io.flow.token.v0.interfaces.Client
) extends Controller with IdentifiedRestController with Helpers {

  def postByProjectId(projectId: String) = Action { request =>
    ProjectsDao.findById(Authorization.All, projectId) match {
      case None => {
        NotFound
      }
      case Some(project) => {
        play.api.Logger.info(s"Received github webook for project[${project.id}] name[${project.name}]")
        MainActor.ref ! MainActor.Messages.ProjectSync(project.id)

        // Find any libaries with the exact name of this project and
        // opportunistically trigger a sync of that library a few
        // times into the future. This supports the normal workflow of
        // tagging a repository and then publishing a new version of
        // that artifact. We want to pick up that new version
        // reasonably quickly.
        Pager.create { offset =>
          LibrariesDao.findAll(
            Authorization.All,
            artifactId = Some(project.name),
            offset = offset
          )
        }.foreach { library =>
          Seq(30, 60, 120, 180).foreach { seconds =>
            MainActor.ref ! MainActor.Messages.LibrarySyncFuture(library.id, seconds)
          }
        }

        Ok(Json.toJson(Map("result" -> "success")))
      }
    }
  }

}
