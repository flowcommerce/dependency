package controllers

import io.flow.dependency.v0.errors.UnitResponse
import io.flow.dependency.v0.models.{Membership, MembershipForm, Role}
import io.flow.dependency.www.lib.DependencyClientProvider
import io.flow.play.controllers.{FlowControllerComponents, IdentifiedRequest}
import io.flow.play.util.{PaginatedCollection, Pagination}
import io.flow.util.Config
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class MembersController @javax.inject.Inject() (
  val dependencyClientProvider: DependencyClientProvider,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
)(implicit ec: ExecutionContext)
  extends controllers.BaseController(config, dependencyClientProvider) {

  override def section = Some(io.flow.dependency.www.lib.Section.Members)

  def index(orgKey: String, page: Int = 0) = User.async { implicit request =>
    withOrganization(request, orgKey) { org =>
      for {
        memberships <- dependencyClient(request).memberships.get(
          organization = Some(org.key),
          limit = Pagination.DefaultLimit.toLong + 1L,
          offset = page * Pagination.DefaultLimit.toLong,
        )
      } yield {
        Ok(
          views.html.members.index(
            uiData(request).copy(organization = Some(org.key)),
            org,
            PaginatedCollection(page, memberships),
          ),
        )
      }
    }
  }

  def create(orgKey: String) = User.async { implicit request =>
    withOrganization(request, orgKey) { org =>
      Future {
        Ok(
          views.html.members.create(
            uiData(request).copy(organization = Some(org.key)),
            org,
            MembersController.uiForm,
          ),
        )
      }
    }
  }

  def postCreate(orgKey: String) = User.async { implicit request =>
    withOrganization(request, orgKey) { org =>
      val boundForm = MembersController.uiForm.bindFromRequest()

      organizations(request).flatMap { _ =>
        boundForm.fold(
          formWithErrors =>
            Future {
              Ok(views.html.members.create(uiData(request).copy(organization = Some(org.key)), org, formWithErrors))
            },
          uiForm => {
            dependencyClient(request).users.get(email = Some(uiForm.email)).flatMap { users =>
              users.headOption match {
                case None =>
                  Future {
                    Ok(
                      views.html.members.create(
                        uiData(request).copy(organization = Some(org.key)),
                        org,
                        boundForm,
                        Seq("User with specified email not found"),
                      ),
                    )
                  }
                case Some(user) => {
                  dependencyClient(request).memberships
                    .post(
                      MembershipForm(
                        organization = org.key,
                        userId = user.id,
                        role = Role(uiForm.role),
                      ),
                    )
                    .map { membership =>
                      Redirect(routes.MembersController.index(org.key))
                        .flashing("success" -> s"User added as ${membership.role}")
                    }
                    .recover {
                      case response: io.flow.dependency.v0.errors.GenericErrorResponse => {
                        Ok(
                          views.html.members.create(
                            uiData(request).copy(organization = Some(org.key)),
                            org,
                            boundForm,
                            response.genericError.messages,
                          ),
                        )
                      }
                    }
                }
              }
            }
          },
        )
      }
    }
  }

  def postDelete(orgKey: String, id: String) = User.async { implicit request =>
    withOrganization(request, orgKey) { org =>
      dependencyClient(request).memberships
        .deleteById(id)
        .map { _ =>
          Redirect(routes.MembersController.index(org.key)).flashing("success" -> s"Membership deleted")
        }
        .recover {
          case UnitResponse(404) => {
            Redirect(routes.MembersController.index(org.key)).flashing("warning" -> s"Membership not found")
          }
        }
    }
  }

  def postMakeMember(orgKey: String, id: String) = User.async { implicit request =>
    makeRole(request, orgKey, id, Role.Member)
  }

  def postMakeAdmin(orgKey: String, id: String) = User.async { implicit request =>
    makeRole(request, orgKey, id, Role.Admin)
  }

  def makeRole[T](
    request: IdentifiedRequest[T],
    orgKey: String,
    id: String,
    role: Role,
  ): Future[Result] = {
    withOrganization(request, orgKey) { org =>
      withMembership(org.key, request, id) { membership =>
        dependencyClient(request).memberships
          .post(
            MembershipForm(
              organization = membership.organization.key,
              userId = membership.user.id,
              role = role,
            ),
          )
          .map { membership =>
            Redirect(routes.MembersController.index(membership.organization.key))
              .flashing("success" -> s"User added as ${membership.role}")
          }
          .recover {
            case response: io.flow.dependency.v0.errors.GenericErrorResponse => {

              Redirect(routes.MembersController.index(membership.organization.key))
                .flashing("warning" -> response.genericError.messages.mkString(", "))
            }
          }
      }
    }
  }

  def withMembership[T](
    org: String,
    request: IdentifiedRequest[T],
    id: String,
  )(
    f: Membership => Future[Result],
  ) = {
    dependencyClient(request).memberships
      .getById(id)
      .flatMap { membership =>
        f(membership)
      }
      .recover {
        case UnitResponse(404) => {
          Redirect(routes.MembersController.index(org)).flashing("warning" -> s"Membership not found")
        }
      }
  }
}

object MembersController {

  case class UiForm(
    role: String,
    email: String,
  )

  private val uiForm = Form(
    mapping(
      "role" -> default(nonEmptyText, io.flow.dependency.v0.models.Role.Member.toString),
      "email" -> nonEmptyText,
    )(UiForm.apply)(UiForm.unapply),
  )

}
