package controllers

import db.BinaryVersionsDao
import io.flow.play.controllers.FlowControllerComponents
import io.flow.util.Config
import io.flow.dependency.v0.models.BinaryVersion
import io.flow.dependency.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class BinaryVersions @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  binaryVersionsDao: BinaryVersionsDao,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents
) extends BaseIdentifiedControllerWithFallback {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    binaryId: Option[String],
    projectId: Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = IdentifiedWithFallback {
    Ok(
      Json.toJson(
        binaryVersionsDao.findAll(
          id = id,
          ids = optionals(ids),
          binaryId = binaryId,
          projectId = projectId,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = IdentifiedWithFallback {
    withBinaryVersion(id) { binary =>
      Ok(Json.toJson(binary))
    }
  }

  def withBinaryVersion(id: String)(
    f: BinaryVersion => Result
  ): Result = {
    binaryVersionsDao.findById(id) match {
      case None => {
        NotFound
      }
      case Some(binaryVersion) => {
        f(binaryVersion)
      }
    }
  }
}
