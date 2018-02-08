package controllers

import com.google.inject.Provider
import db.{Authorization, BinaryVersionsDao, TokensDao, UsersDao}
import io.flow.play.controllers.{AuthorizationImpl, FlowController, FlowControllerComponents}
import io.flow.common.v0.models.UserReference
import io.flow.play.util.{Config, Validation}
import io.flow.dependency.v0.models.BinaryVersion
import io.flow.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class BinaryVersions @javax.inject.Inject()(
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
  ) = IdentifiedWithFallback { request =>
    Ok(
      Json.toJson(
        binaryVersionsDao.findAll(
          Authorization.User(request.user.id),
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

  def getById(id: String) = IdentifiedWithFallback { request =>
    withBinaryVersion(request.user, id) { binary =>
      Ok(Json.toJson(binary))
    }
  }

  def withBinaryVersion(user: UserReference, id: String)(
    f: BinaryVersion => Result
  ): Result = {
    binaryVersionsDao.findById(Authorization.User(user.id), id) match {
      case None => {
        NotFound
      }
      case Some(binaryVersion) => {
        f(binaryVersion)
      }
    }
  }
}

