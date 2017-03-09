package controllers

import com.bryzek.dependency.v0.errors.UnitResponse
import com.bryzek.dependency.v0.models.{Organization, Resolver, ResolverForm, UsernamePassword, Visibility}
import com.bryzek.dependency.www.lib.DependencyClientProvider
import io.flow.play.util.{Pagination, PaginatedCollection}
import scala.concurrent.Future

import play.api._
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

class ResolversController @javax.inject.Inject() (
  val messagesApi: MessagesApi,
  override val dependencyClientProvider: DependencyClientProvider
) extends BaseController(tokenClient, dependencyClientProvider) {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def section = Some(com.bryzek.dependency.www.lib.Section.Resolvers)

  def index(page: Int = 0) = Identified.async { implicit request =>
    for {
      resolvers <- dependencyClient(request).resolvers.get(
        limit = Pagination.DefaultLimit+1,
        offset = page * Pagination.DefaultLimit
      )
    } yield {
      Ok(
        views.html.resolvers.index(
          uiData(request),
          PaginatedCollection(page, resolvers)
        )
      )
    }
  }

  def show(id: String, librariesPage: Int = 0) = Identified.async { implicit request =>
    withResolver(request, id) { resolver =>
      for {
        libraries <- dependencyClient(request).libraries.get(
          resolverId = Some(id),
          limit = Pagination.DefaultLimit+1,
          offset = librariesPage * Pagination.DefaultLimit
        )
      } yield {
        Ok(
          views.html.resolvers.show(
            uiData(request),
            resolver,
            PaginatedCollection(librariesPage, libraries)
          )
        )
      }
    }
  }

  def create() = Identified.async { implicit request =>
    organizations(request).map { orgs =>
      Ok(
        views.html.resolvers.create(
          uiData(request), ResolversController.uiForm, orgs
        )
      )
    }
  }

  def postCreate() = Identified.async { implicit request =>
    val boundForm = ResolversController.uiForm.bindFromRequest

    organizations(request).flatMap { orgs =>
      boundForm.fold (

        formWithErrors => Future {
          Ok(views.html.resolvers.create(uiData(request), formWithErrors, orgs))
        },

        uiForm => {
          dependencyClient(request).resolvers.post(
            resolverForm = uiForm.resolverForm()
          ).map { resolver =>
            Redirect(routes.ResolversController.show(resolver.id)).flashing("success" -> "Resolver created")
          }.recover {
            case response: com.bryzek.dependency.v0.errors.ErrorsResponse => {
              Ok(views.html.resolvers.create(uiData(request), boundForm, orgs, response.errors.map(_.message)))
            }
          }
        }
      )
    }
  }

  def withResolver[T](
    request: IdentifiedRequest[T],
    id: String
  )(
    f: Resolver => Future[Result]
  ) = {
    dependencyClient(request).resolvers.getById(id).flatMap { resolver =>
      f(resolver)
    }.recover {
      case UnitResponse(404) => {
        Redirect(routes.ResolversController.index()).flashing("warning" -> s"Resolver not found")
      }
    }
  }

  def postDelete(id: String) = Identified.async { implicit request =>
    dependencyClient(request).resolvers.deleteById(id).map { response =>
      Redirect(routes.ResolversController.index()).flashing("success" -> s"Resolver deleted")
    }.recover {
      case UnitResponse(404) => {
        Redirect(routes.ResolversController.index()).flashing("warning" -> s"Resolver not found")
      }
    }
  }

}

object ResolversController {

  case class UiForm(
    organization: String,
    uri: String,
    username: Option[String],
    password: Option[String]
  ) {

    def resolverForm() = ResolverForm(
      organization = organization,
      visibility = Visibility.Private,
      uri = uri,
      credentials = credentials
    )

    val credentials = username.map(_.trim) match {
      case None => None
      case Some("") => None
      case Some(username) => {
        Some(
          UsernamePassword(
            username = username,
            password = password.map(_.trim)
          )
        )
      }
    }

  }

  private val uiForm = Form(
    mapping(
      "organization" -> nonEmptyText,
      "uri" -> nonEmptyText,
      "username" -> optional(text),
      "password" -> optional(text)
    )(UiForm.apply)(UiForm.unapply)
  )

}
