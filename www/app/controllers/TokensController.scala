package controllers

import com.bryzek.dependency.v0.errors.UnitResponse
import com.bryzek.dependency.v0.models.{Token, TokenForm}
import com.bryzek.dependency.www.lib.DependencyClientProvider
import io.flow.common.v0.models.User
import io.flow.play.util.{Pagination, PaginatedCollection}
import scala.concurrent.Future

import play.api._
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

class TokensController @javax.inject.Inject() (
  val messagesApi: MessagesApi,
  override val dependencyClientProvider: DependencyClientProvider
) extends BaseController(tokenClient, dependencyClientProvider) {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def section = None

  def index(page: Int = 0) = Identified.async { implicit request =>
    for {
      tokens <- dependencyClient(request).tokens.get(
        limit = Pagination.DefaultLimit+1,
        offset = page * Pagination.DefaultLimit
      )
    } yield {
      Ok(views.html.tokens.index(uiData(request), PaginatedCollection(page, tokens)))
    }
  }

  def show(id: String) = Identified.async { implicit request =>
    withToken(request, id) { token =>
      Future {
        Ok(views.html.tokens.show(uiData(request), token))
      }
    }
  }

  def create() = Identified { implicit request =>
    Ok(views.html.tokens.create(uiData(request), TokensController.tokenForm))
  }

  def postCreate = Identified.async { implicit request =>
    val form = TokensController.tokenForm.bindFromRequest
    form.fold (

      errors => Future {
        Ok(views.html.tokens.create(uiData(request), errors))
      },

      valid => {
        dependencyClient(request).tokens.post(
          TokenForm(
            userId = request.user.id,
            description = valid.description
          )
        ).map { token =>
          Redirect(routes.TokensController.show(token.id)).flashing("success" -> "Token created")
        }.recover {
          case r: com.bryzek.dependency.v0.errors.ErrorsResponse => {
            Ok(views.html.tokens.create(uiData(request), form, r.errors.map(_.message)))
          }
        }
      }

    )
  }

  def postDelete(id: String) = Identified.async { implicit request =>
    dependencyClient(request).tokens.deleteById(id).map { response =>
      Redirect(routes.TokensController.index()).flashing("success" -> s"Token deleted")
    }.recover {
      case UnitResponse(404) => {
        Redirect(routes.TokensController.index()).flashing("warning" -> s"Token not found")
      }
    }
  }

  def withToken[T](
    request: IdentifiedRequest[T],
    id: String
  )(
    f: Token => Future[Result]
  ) = {
    dependencyClient(request).tokens.getById(id).flatMap { token =>
      f(token)
    }.recover {
      case UnitResponse(404) => {
        Redirect(routes.TokensController.index()).flashing("warning" -> s"Token not found")
      }
    }
  }

}

object TokensController {

  case class TokenData(
    description: Option[String]
  )

  private[controllers] val tokenForm = Form(
    mapping(
      "description" -> optional(nonEmptyText)
    )(TokenData.apply)(TokenData.unapply)
  )
}
