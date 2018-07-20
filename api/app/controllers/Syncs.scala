package controllers

import controllers.helpers.{BinaryHelper, LibrariesHelper, ProjectHelper}
import db.{Authorization, LibrariesDao, SyncsDao}
import io.flow.dependency.actors.MainActor
import io.flow.dependency.v0.models.SyncEvent
import io.flow.dependency.v0.models.json._
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.Config
import lib.SyncsService
import play.api.mvc._
import play.api.libs.json._
import play.api.mvc._

@javax.inject.Singleton
class Syncs @javax.inject.Inject()(
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  syncsDao: SyncsDao,
  librariesHelper: LibrariesHelper,
  binaryHelper: BinaryHelper,
  projectHelper: ProjectHelper,
  librariesDao: LibrariesDao,
  @javax.inject.Named("main-actor") mainActor: akka.actor.ActorRef,
  syncsService: SyncsService,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents
) extends BaseIdentifiedControllerWithFallback {

  def get(
    objectId: Option[String],
    event: Option[SyncEvent],
    limit: Long = 25,
    offset: Long = 0
  ) = IdentifiedWithFallback { request =>
    Ok(
      Json.toJson(
        syncsDao.findAll(
          objectId = objectId,
          event = event,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def postAll() = IdentifiedWithFallback { request =>
    mainActor ! MainActor.Messages.SyncAll
    NoContent
  }

  def postBinariesById(id: String) = IdentifiedWithFallback { request =>
    binaryHelper.withBinary(request.user, id) { binary =>
      mainActor ! MainActor.Messages.BinarySync(binary.id)
      NoContent
    }
  }

  def postLibrariesById(id: String) = IdentifiedWithFallback { request =>
    syncsService.syncLibrary(id, request.user).map(_ => Ok).getOrElse(NotFound)
  }

  def postProjectsById(id: String) = IdentifiedWithFallback { request =>
    projectHelper.withProject(request.user, id) { project =>
      mainActor ! MainActor.Messages.ProjectSync(id)
      NoContent
    }
  }

  def postLibraries(group_id: Option[String]) = IdentifiedWithFallback { request =>
    syncsService.syncLibraries(group_id, request.user)
    NoContent
  }
}
