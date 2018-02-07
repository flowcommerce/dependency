package controllers

import controllers.helpers.{BinaryHelper, LibrariesHelper, ProjectHelper}
import db.SyncsDao
import io.flow.dependency.actors.MainActor
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.dependency.v0.models.SyncEvent
import io.flow.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import io.flow.play.util.Config
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Syncs @javax.inject.Inject()(
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  syncsDao: SyncsDao,
  librariesHelper: LibrariesHelper,
  binaryHelper: BinaryHelper,
  projectHelper: ProjectHelper,
  @javax.inject.Named("main-actor") mainActor: akka.actor.ActorRef
) extends FlowController {

  def get(
    objectId: Option[String],
    event: Option[SyncEvent],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
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

  def postBinariesById(id: String) = Identified { request =>
    binaryHelper.withBinary(request.user, id) { binary =>
      mainActor ! MainActor.Messages.BinarySync(binary.id)
      NoContent
    }
  }

  def postLibrariesById(id: String) = Identified { request =>
    librariesHelper.withLibrary(request.user, id) { library =>
      mainActor ! MainActor.Messages.LibrarySync(library.id)
      NoContent
    }
  }

  def postProjectsById(id: String) = Identified { request =>
    projectHelper.withProject(request.user, id) { project =>
      mainActor ! MainActor.Messages.ProjectSync(id)
      NoContent
    }
  }

}
