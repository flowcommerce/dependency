package controllers

import io.flow.dependency.v0.errors.UnitResponse
import io.flow.dependency.v0.models.{Token, TokenForm}
import io.flow.dependency.www.lib.DependencyClientProvider
import io.flow.play.controllers.{FlowControllerComponents, IdentifiedRequest}
import io.flow.play.util.{PaginatedCollection, Pagination}
import io.flow.util.Config
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class TokensController @javax.inject.Inject() (
  val dependencyClientProvider: DependencyClientProvider,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
)(implicit ec: ExecutionContext)
  extends controllers.BaseController(config, dependencyClientProvider) {

  override def section = None

  def index(page: Int = 0) = User.async { implicit request =>
    for {
      tokens <- dependencyClient(request).tokens.get(
        limit = Pagination.DefaultLimit.toLong + 1L,
        offset = page * Pagination.DefaultLimit.toLong,
      )
    } yield {
      Ok(views.html.tokens.index(uiData(request), PaginatedCollection(page, tokens)))
    }
  }

  def show(id: String) = User.async { implicit request =>
    withToken(request, id) { token =>
      Future {
        Ok(views.html.tokens.show(uiData(request), token))
      }
    }
  }

  def create() = User { implicit request =>
    Ok(views.html.tokens.create(uiData(request), TokensController.tokenForm))
  }

  def postCreate = User.async { implicit request =>
    val form = TokensController.tokenForm.bindFromRequest()
    form.fold(
      errors =>
        Future {
          Ok(views.html.tokens.create(uiData(request), errors))
        },
      valid => {
        dependencyClient(request).tokens
          .post(
            TokenForm(
              userId = request.user.id,
              description = valid.description,
            ),
          )
          .map { token =>
            Redirect(routes.TokensController.show(token.id)).flashing("success" -> "Token created")
          }
          .recover {
            case r: io.flow.dependency.v0.errors.GenericErrorResponse => {
              Ok(views.html.tokens.create(uiData(request), form, r.genericError.messages))
            }
          }
      },
    )
  }

  def postDelete(id: String) = User.async { implicit request =>
    dependencyClient(request).tokens
      .deleteById(id)
      .map { _ =>
        Redirect(routes.TokensController.index()).flashing("success" -> s"Token deleted")
      }
      .recover {
        case UnitResponse(404) => {
          Redirect(routes.TokensController.index()).flashing("warning" -> s"Token not found")
        }
      }
  }

  def withToken[T](
    request: IdentifiedRequest[T],
    id: String,
  )(
    f: Token => Future[Result],
  ) = {
    dependencyClient(request).tokens
      .getById(id)
      .flatMap { token =>
        f(token)
      }
      .recover {
        case UnitResponse(404) => {
          Redirect(routes.TokensController.index()).flashing("warning" -> s"Token not found")
        }
      }
  }

}

object TokensController {

  case class TokenData(
    description: Option[String],
  )

  private[controllers] val tokenForm = Form(
    mapping(
      "description" -> optional(nonEmptyText),
    )(TokenData.apply)(TokenData.unapply),
  )
}
