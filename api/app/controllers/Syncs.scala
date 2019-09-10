package controllers

import controllers.helpers.{BinaryHelper, LibrariesHelper, ProjectHelper}
import db.{Authorization, InternalTask, InternalTasksDao, LibrariesDao, SyncsDao}
import io.flow.dependency.v0.models.SyncEvent
import io.flow.dependency.v0.models.json._
import io.flow.play.controllers.FlowControllerComponents
import io.flow.util.Config
import play.api.libs.json.Json
import play.api.mvc._

@javax.inject.Singleton
class Syncs @javax.inject.Inject()(
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents,
  internalTasksDao: InternalTasksDao,
  syncsDao: SyncsDao,
  librariesHelper: LibrariesHelper,
  binaryHelper: BinaryHelper,
  projectHelper: ProjectHelper,
  librariesDao: LibrariesDao,
) extends BaseIdentifiedControllerWithFallback {

  def get(
    objectId: Option[String],
    event: Option[SyncEvent],
    limit: Long = 25,
    offset: Long = 0
  ) = IdentifiedWithFallback {
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

  def postAll() = IdentifiedWithFallback {
    internalTasksDao.queueAll()
    NoContent
  }

  def postBinariesById(id: String) = IdentifiedWithFallback {
    binaryHelper.withBinary(id) { binary =>
      internalTasksDao.queueBinary(binary)
      NoContent
    }
  }

  def postLibrariesById(id: String) = IdentifiedWithFallback { request =>
    librariesHelper.withLibrary(request.user, id) { library =>
      internalTasksDao.queueLibrary(library, priority = InternalTask.HighestPriority)
      NoContent
    }
  }

  def postProjectsById(id: String) = IdentifiedWithFallback { request =>
    projectHelper.withProject(request.user, id) { project =>
      internalTasksDao.queueProject(project, priority = InternalTask.HighestPriority)
      NoContent
    }
  }

  def postLibraries(group_id: Option[String]) = IdentifiedWithFallback { request =>
    val auth = Authorization.User(request.user.id)
    val libsToSync = librariesDao.findAll(auth, groupId = group_id, limit = 1000)
    libsToSync.foreach { library =>
      internalTasksDao.queueLibrary(library, priority = InternalTask.HighestPriority)
    }
    NoContent
  }
}
