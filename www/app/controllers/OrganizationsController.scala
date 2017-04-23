package controllers

import com.bryzek.dependency.v0.errors.UnitResponse
import com.bryzek.dependency.v0.models.{Organization, OrganizationForm}
import com.bryzek.dependency.www.lib.DependencyClientProvider
import io.flow.play.util.{Pagination, PaginatedCollection}
import scala.concurrent.Future

import play.api._
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

class OrganizationsController @javax.inject.Inject() (
  val messagesApi: MessagesApi,
  override val dependencyClientProvider: DependencyClientProvider
) extends BaseController(tokenClient, dependencyClientProvider) {

  import scala.concurrent.ExecutionContext.Implicits.global
 
  override def section = None

  def redirectToDashboard(org: String) = Identified { implicit request =>
    Redirect(routes.ApplicationController.index(organization = Some(org)))
  }

  def index(page: Int = 0) = Identified.async { implicit request =>
    for {
      organizations <- dependencyClient(request).organizations.get(
        limit = Pagination.DefaultLimit+1,
        offset = page * Pagination.DefaultLimit
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

  def show(key: String, projectsPage: Int = 0) = Identified.async { implicit request =>
    withOrganization(request, key) { org =>
      for {
        projects <- dependencyClient(request).projects.get(
          organization = Some(key),
          limit = Pagination.DefaultLimit+1,
          offset = projectsPage * Pagination.DefaultLimit
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

  def create() = Identified { implicit request =>
    Ok(
      views.html.organizations.create(
        uiData(request),
        OrganizationsController.uiForm
      )
    )
  }

  def postCreate() = Identified.async { implicit request =>
    val boundForm = OrganizationsController.uiForm.bindFromRequest
    boundForm.fold (

      formWithErrors => Future {
        Ok(views.html.organizations.create(uiData(request), formWithErrors))
      },

      uiForm => {
        dependencyClient(request).organizations.post(uiForm.organizationForm).map { organization =>
          Redirect(routes.OrganizationsController.show(organization.key)).flashing("success" -> "Organization created")
        }.recover {
          case response: com.bryzek.dependency.v0.errors.ErrorsResponse => {
            Ok(views.html.organizations.create(uiData(request), boundForm, response.errors.map(_.message)))
          }
        }
      }
    )
  }

  def edit(key: String) = Identified.async { implicit request =>
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

  def postEdit(key: String) = Identified.async { implicit request =>
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
            case response: com.bryzek.dependency.v0.errors.ErrorsResponse => {
              Ok(views.html.organizations.edit(uiData(request), organization, boundForm, response.errors.map(_.message)))
            }
          }
        }
      )
    }
  }

  def postDelete(key: String) = Identified.async { implicit request =>
    withOrganization(request, key) { org =>
      dependencyClient(request).organizations.deleteById(org.id).map { response =>
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
