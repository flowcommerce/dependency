package controllers

import io.flow.dependency.v0.errors.UnitResponse
import io.flow.dependency.v0.models.{Resolver, ResolverForm, UsernamePassword, Visibility}
import io.flow.dependency.www.lib.DependencyClientProvider
import io.flow.play.controllers.{FlowControllerComponents, IdentifiedRequest}
import io.flow.play.util.{PaginatedCollection, Pagination}
import io.flow.util.Config
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class ResolversController @javax.inject.Inject() (
  val dependencyClientProvider: DependencyClientProvider,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents
)(implicit ec: ExecutionContext) extends controllers.BaseController(config, dependencyClientProvider) {

  override def section = Some(io.flow.dependency.www.lib.Section.Resolvers)

  def index(page: Int = 0) = User.async { implicit request =>
    for {
      resolvers <- dependencyClient(request).resolvers.get(
        limit = Pagination.DefaultLimit.toLong + 1L,
        offset = page * Pagination.DefaultLimit.toLong
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

  def show(id: String, librariesPage: Int = 0) = User.async { implicit request =>
    withResolver(request, id) { resolver =>
      for {
        libraries <- dependencyClient(request).libraries.get(
          resolverId = Some(id),
          limit = Pagination.DefaultLimit.toLong + 1L,
          offset = librariesPage * Pagination.DefaultLimit.toLong
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

  def create() = User.async { implicit request =>
    organizations(request).map { orgs =>
      Ok(
        views.html.resolvers.create(
          uiData(request), ResolversController.uiForm, orgs
        )
      )
    }
  }

  def postCreate() = User.async { implicit request =>
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
            case response: io.flow.dependency.v0.errors.GenericErrorsResponse => {
              Ok(views.html.resolvers.create(uiData(request), boundForm, orgs, response.genericErrors.flatMap(_.messages)))
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

  def postDelete(id: String) = User.async { implicit request =>
    dependencyClient(request).resolvers.deleteById(id).map { _ =>
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
