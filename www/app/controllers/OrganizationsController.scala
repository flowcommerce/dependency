package controllers

import io.flow.dependency.v0.errors.UnitResponse
import io.flow.dependency.v0.models.OrganizationForm
import io.flow.dependency.www.lib.DependencyClientProvider
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.{PaginatedCollection, Pagination}
import io.flow.util.Config
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class OrganizationsController @javax.inject.Inject() (
  val dependencyClientProvider: DependencyClientProvider,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents
)(implicit ec: ExecutionContext) extends BaseController(config, dependencyClientProvider) {

  override def section = None

  def redirectToDashboard(org: String) = User {
    Redirect(routes.ApplicationController.index(organization = Some(org)))
  }

  def index(page: Int = 0) = User.async { implicit request =>
    for {
      organizations <- dependencyClient(request).organizations.get(
        limit = Pagination.DefaultLimit.toLong + 1L,
        offset = page * Pagination.DefaultLimit.toLong
      )
    } yield {
      Ok(
        views.html.organizations.index(
          uiData(request),
          PaginatedCollection(page, organizations)
        )
      )
    }
  }

  def show(key: String, projectsPage: Int = 0) = User.async { implicit request =>
    withOrganization(request, key) { org =>
      for {
        projects <- dependencyClient(request).projects.get(
          organization = Some(key),
          limit = Pagination.DefaultLimit.toLong + 1L,
          offset = projectsPage * Pagination.DefaultLimit.toLong
        )
      } yield {
        Ok(
          views.html.organizations.show(
            uiData(request),
            org,
            PaginatedCollection(projectsPage, projects)
          )
        )
      }
    }
  }

  def create() = User { implicit request =>
    Ok(
      views.html.organizations.create(
        uiData(request),
        OrganizationsController.uiForm
      )
    )
  }

  def postCreate() = User.async { implicit request =>
    val boundForm = OrganizationsController.uiForm.bindFromRequest
    boundForm.fold (

      formWithErrors => Future {
        Ok(views.html.organizations.create(uiData(request), formWithErrors))
      },

      uiForm => {
        dependencyClient(request).organizations.post(uiForm.organizationForm).map { organization =>
          Redirect(routes.OrganizationsController.show(organization.key)).flashing("success" -> "Organization created")
        }.recover {
          case response: io.flow.dependency.v0.errors.GenericErrorsResponse => {
            Ok(views.html.organizations.create(uiData(request), boundForm, response.genericErrors.flatMap(_.messages)))
          }
        }
      }
    )
  }

  def edit(key: String) = User.async { implicit request =>
    withOrganization(request, key) { organization =>
      Future {
        Ok(
          views.html.organizations.edit(
            uiData(request),
            organization,
            OrganizationsController.uiForm.fill(
              OrganizationsController.UiForm(
                key = organization.key
              )
            )
          )
        )
      }
    }
  }

  def postEdit(key: String) = User.async { implicit request =>
    withOrganization(request, key) { organization =>
      val boundForm = OrganizationsController.uiForm.bindFromRequest
      boundForm.fold (

        formWithErrors => Future {
          Ok(views.html.organizations.edit(uiData(request), organization, formWithErrors))
        },

        uiForm => {
          dependencyClient(request).organizations.putById(organization.id, uiForm.organizationForm).map { updated =>
            Redirect(routes.OrganizationsController.show(updated.key)).flashing("success" -> "Organization updated")
          }.recover {
            case response: io.flow.dependency.v0.errors.GenericErrorsResponse => {
              Ok(views.html.organizations.edit(uiData(request), organization, boundForm, response.genericErrors.flatMap(_.messages)))
            }
          }
        }
      )
    }
  }

  def postDelete(key: String) = User.async { implicit request =>
    withOrganization(request, key) { org =>
      dependencyClient(request).organizations.deleteById(org.id).map { _ =>
        Redirect(routes.OrganizationsController.index()).flashing("success" -> s"Organization deleted")
      }.recover {
        case UnitResponse(404) => {
          Redirect(routes.OrganizationsController.index()).flashing("warning" -> s"Organization not found")
        }
      }
    }
  }

}

object OrganizationsController {

  case class UiForm(
    key: String
  ) {

    val organizationForm = OrganizationForm(
      key = key
    )

  }

  private val uiForm = Form(
    mapping(
      "key" -> nonEmptyText
    )(UiForm.apply)(UiForm.unapply)
  )

}
