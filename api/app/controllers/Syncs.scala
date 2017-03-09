package controllers

import db.SyncsDao
import com.bryzek.dependency.actors.MainActor
import io.flow.play.controllers.FlowController
import com.bryzek.dependency.v0.models.SyncEvent
import com.bryzek.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Syncs @javax.inject.Inject() (
  override val config: io.flow.play.util.Config,
) extends Controller with FlowController with Helpers {

  def get(
    objectId: Option[String],
    event: Option[SyncEvent],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
    Ok(
      Json.toJson(
        SyncsDao.findAll(
          objectId = objectId,
          event = event,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def postBinariesById(id: String) = Identified { request =>
    withBinary(request.user, id) { binary =>
      MainActor.ref ! MainActor.Messages.BinarySync(binary.id)
      NoContent
    }
  }

  def postLibrariesById(id: String) = Identified { request =>
    withLibrary(request.user, id) { library =>
      MainActor.ref ! MainActor.Messages.LibrarySync(library.id)
      NoContent
    }
  }

  def postProjectsById(id: String) = Identified { request =>
    withProject(request.user, id) { project =>
      MainActor.ref ! MainActor.Messages.ProjectSync(id)
      NoContent
    }
  }

}
