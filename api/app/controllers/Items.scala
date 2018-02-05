package controllers

import db.ItemsDao
import io.flow.dependency.v0.models.json._
import io.flow.play.controllers.{FlowController, FlowControllerComponents}
import io.flow.play.util.Config
import play.api.mvc._
import play.api.libs.json._

@javax.inject.Singleton
class Items @javax.inject.Inject() (
  tokenClient: io.flow.token.v0.interfaces.Client ,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  itemsDao: ItemsDao
) extends FlowController with BaseIdentifiedController {

  def get(
    q: Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = Identified { request =>
    Ok(
      Json.toJson(
        itemsDao.findAll(
          authorization(request),
          q = q,
          limit = limit,
          offset = offset
        )
      )
    )
  }

}
