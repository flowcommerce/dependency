package controllers

import db.InternalTasksDao
import io.flow.dependency.v0.anorm.parsers.TaskData
import io.flow.dependency.v0.models.TaskDataDiscriminator.TaskDataSync
import io.flow.dependency.v0.models.{SyncEvent, TaskData}
import io.flow.dependency.v0.models.json._
import io.flow.play.controllers.FlowControllerComponents
import io.flow.util.Config
import play.api.mvc._

@javax.inject.Singleton
class Syncs @javax.inject.Inject()(
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents,
  internalTasksDao: InternalTasksDao
) extends BaseIdentifiedControllerWithFallback {

  def postAll() = IdentifiedWithFallback {
    internalTasksDao.createSyncAllIfNotQueued()
    NoContent
  }

  def postBinariesById(id: String) = IdentifiedWithFallback {
    binaryHelper.withBinary(id) { binary =>
      internalTasksDao.createSyncAllIfNotQueued()
      mainActor ! MainActor.Messages.BinarySync(binary.id)
      NoContent
    }
  }

  def postLibrariesById(id: String) = IdentifiedWithFallback { request =>
    librariesHelper.withLibrary(request.user, id) { library =>
      mainActor ! MainActor.Messages.LibrarySync(library.id)
      NoContent
    }
  }

  def postProjectsById(id: String) = IdentifiedWithFallback { request =>
    projectHelper.withProject(request.user, id) { _ =>
      mainActor ! MainActor.Messages.ProjectSync(id)
      NoContent
    }
  }

  def postLibraries(group_id: Option[String]) = IdentifiedWithFallback { request =>
    val auth = Authorization.User(request.user.id)
    val libsToSync = librariesDao.findAll(auth, groupId = group_id, limit = 1000)
    libsToSync.foreach { lib =>
      mainActor ! MainActor.Messages.LibrarySync(lib.id)
    }
    NoContent
  }
}
