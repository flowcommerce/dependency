package controllers

import db.InternalItemsDao
import io.flow.dependency.v0.models.json._
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.Config
import play.api.libs.json._
import play.api.mvc._

@javax.inject.Singleton
class Items @javax.inject.Inject() (
                                     val config: Config,
                                     val controllerComponents: ControllerComponents,
                                     val flowControllerComponents: FlowControllerComponents,
                                     itemsDao: InternalItemsDao,
                                     val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents
) extends BaseIdentifiedControllerWithFallback with BaseIdentifiedController {

  def get(
    q: Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = IdentifiedWithFallback { request =>
    Ok(
      Json.toJson(
        itemsDao.findAll(
          authorization(request),
          q = q,
          limit = Some(limit),
          offset = offset
        )
      )
    )
  }

}
