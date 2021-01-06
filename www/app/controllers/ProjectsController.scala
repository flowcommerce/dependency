package controllers

import com.github.ghik.silencer.silent
import io.flow.dependency.v0.errors.{GenericErrorResponse, UnitResponse}
import io.flow.dependency.v0.models._
import io.flow.dependency.www.lib.DependencyClientProvider
import io.flow.play.controllers.{FlowControllerComponents, IdentifiedRequest}
import io.flow.play.util.{PaginatedCollection, Pagination}
import io.flow.util.Config
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class ProjectsController @javax.inject.Inject()(
  val dependencyClientProvider: DependencyClientProvider,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents
)(implicit ec: ExecutionContext) extends controllers.BaseController(config, dependencyClientProvider) {

  override def section = Some(io.flow.dependency.www.lib.Section.Projects)

  def index(page: Int = 0) = User.async { implicit request =>
    for {
      projects <- dependencyClient(request).projects.get(
        limit = Pagination.DefaultLimit.toLong + 1L,
        offset = page * Pagination.DefaultLimit.toLong
      )
    } yield {
      Ok(
        views.html.projects.index(
          uiData(request),
          PaginatedCollection(page, projects)
        )
      )
    }
  }

  def show(id: String, recommendationsPage: Int = 0, binariesPage: Int = 0, librariesPage: Int = 0) = User.async { implicit request =>
    withProject(request, id) { project =>
      for {
        recommendations <- dependencyClient(request).recommendations.get(
          projectId = Some(project.id),
          limit = Pagination.DefaultLimit.toLong + 1L,
          offset = recommendationsPage * Pagination.DefaultLimit.toLong
        )
        projectBinaries <- dependencyClient(request).projectBinaries.get(
          projectId = Some(id),
          limit = Pagination.DefaultLimit.toLong + 1L,
          offset = binariesPage * Pagination.DefaultLimit.toLong
        )
        projectLibraries <- dependencyClient(request).projectLibraries.get(
          projectId = Some(id),
          limit = Pagination.DefaultLimit.toLong + 1L,
          offset = librariesPage * Pagination.DefaultLimit.toLong
        )
        syncs <- dependencyClient(request).syncs.get(
          objectId = Some(id),
          event = Some(SyncEvent.Completed),
          limit = 1
        )
      } yield {
        Ok(
          views.html.projects.show(
            uiData(request),
            project,
            PaginatedCollection(recommendationsPage, recommendations),
            PaginatedCollection(binariesPage, projectBinaries),
            PaginatedCollection(librariesPage, projectLibraries),
            syncs.headOption
          )
        )
      }
    }
  }

  def github() = User.async { implicit request =>
    for {
      orgs <- organizations(request)
    } yield {
      orgs match {
        case Nil => {
          Redirect(routes.OrganizationsController.index()).flashing("warning" -> "Add a new org before adding a project")
        }
        case one :: Nil => {
          Redirect(routes.ProjectsController.githubOrg(one.key))
        }
        case multiple => {
          Ok(
            views.html.projects.github(
              uiData(request), multiple
            )
          )
        }
      }
    }
  }

  def githubOrg(orgKey: String, repositoriesPage: Int = 0) = User.async { implicit request =>
    withOrganization(request, orgKey) { org =>
      for {
        repositories <- dependencyClient(request).repositories.getGithub(
          organizationId = Some(org.id),
          existingProject = Some(false),
          limit = Pagination.DefaultLimit.toLong + 1L,
          offset = repositoriesPage * Pagination.DefaultLimit.toLong
        )
      } yield {
        Ok(
          views.html.projects.githubOrg(
            uiData(request), org, PaginatedCollection(repositoriesPage, repositories)
          )
        )
      }
    }
  }

  @silent
  def postGithubOrg(
    orgKey: String,
    owner: String, // github owner, ex. flowcommerce    
    name: String, // github repo name, ex. user
    repositoriesPage: Int = 0
  ) = User.async { implicit request =>
    withOrganization(request, orgKey) { org =>
      dependencyClient(request).repositories.getGithub(
        organizationId = Some(org.id),
        owner = Some(owner),
        name = Some(name)
      ).flatMap { selected =>
        selected.headOption match {
          case None => Future {
            Redirect(routes.ProjectsController.github()).flashing("warning" -> "Project not found")
          }
          case Some(repo) => {
            dependencyClient(request).projects.post(
              ProjectForm(
                organization = org.key,
                name = repo.name,
                scms = Scms.Github,
                visibility = if (repo.`private`) {
                  Visibility.Private
                } else {
                  Visibility.Public
                },
                uri = repo.htmlUrl
              )
            ).map { project =>
              Redirect(routes.ProjectsController.sync(project.id)).flashing("success" -> "Project added")
            }
          }
        }
      }
    }
  }

  def create() = User.async { implicit request =>
    organizations(request).map { orgs =>
      Ok(
        views.html.projects.create(
          uiData(request),
          ProjectsController.uiForm,
          orgs
        )
      )
    }
  }

  def postCreate() = User.async { implicit request =>
    val boundForm = ProjectsController.uiForm.bindFromRequest()

    organizations(request).flatMap { orgs =>
      boundForm.fold(

        formWithErrors => Future {
          Ok(views.html.projects.create(uiData(request), formWithErrors, orgs))
        },

        uiForm => {
          dependencyClient(request).projects.post(
            projectForm = ProjectForm(
              organization = uiForm.organization,
              name = uiForm.name,
              scms = Scms(uiForm.scms),
              visibility = Visibility(uiForm.visibility),
              uri = uiForm.uri
            )
          ).map { project =>
            Redirect(routes.ProjectsController.sync(project.id)).flashing("success" -> "Project created")
          }.recover {
            case response: io.flow.dependency.v0.errors.GenericErrorResponse => {
              Ok(views.html.projects.create(uiData(request), boundForm, orgs, response.genericError.messages))
            }
          }
        }
      )
    }
  }

  def edit(id: String) = User.async { implicit request =>
    withProject(request, id) { project =>
      organizations(request).map { orgs =>
        Ok(
          views.html.projects.edit(
            uiData(request),
            project,
            ProjectsController.uiForm.fill(
              ProjectsController.UiForm(
                organization = project.organization.key,
                name = project.name,
                scms = project.scms.toString,
                visibility = project.visibility.toString,
                uri = project.uri
              )
            ),
            orgs
          )
        )
      }
    }
  }

  def postEdit(id: String) = User.async { implicit request =>
    organizations(request).flatMap { orgs =>
      withProject(request, id) { project =>
        val boundForm = ProjectsController.uiForm.bindFromRequest()
        boundForm.fold(

          formWithErrors => Future {
            Ok(views.html.projects.edit(uiData(request), project, formWithErrors, orgs))
          },

          uiForm => {
            dependencyClient(request).projects.putById(
              project.id,
              ProjectForm(
                organization = project.organization.key,
                name = uiForm.name,
                scms = Scms(uiForm.scms),
                visibility = Visibility(uiForm.visibility),
                uri = uiForm.uri
              )
            ).map { project =>
              Redirect(routes.ProjectsController.show(project.id)).flashing("success" -> "Project updated")
            }.recover {
              case response: io.flow.dependency.v0.errors.GenericErrorResponse => {
                Ok(views.html.projects.edit(uiData(request), project, boundForm, orgs, response.genericError.messages))
              }
            }
          }
        )
      }
    }
  }

  def postDelete(id: String) = User.async { implicit request =>
    dependencyClient(request).projects.deleteById(id).map { _ =>
      Redirect(routes.ProjectsController.index()).flashing("success" -> s"Project deleted")
    }.recover {
      case UnitResponse(404) => {
        Redirect(routes.ProjectsController.index()).flashing("warning" -> s"Project not found")
      }
      case GenericErrorResponse(_, msg) => {
        Redirect(routes.ProjectsController.index()).flashing("error" -> msg.getOrElse("Error deleting project"))
      }
    }
  }

  /**
    * Waits for the latest sync to complete for this project.
    */
  @silent
  def sync(id: String, n: Int, librariesPage: Int = 0) = User.async { implicit request =>
    withProject(request, id) { _ =>
      for {
        syncs <- dependencyClient(request).syncs.get(
          objectId = Some(id)
        )
        pendingProjectLibraries <- dependencyClient(request).projectLibraries.get(
          projectId = Some(id),
          isSynced = Some(false),
          limit = 100
        )
        completedProjectLibraries <- dependencyClient(request).projectLibraries.get(
          projectId = Some(id),
          isSynced = Some(true),
          limit = 100
        )
        pendingProjectBinaries <- dependencyClient(request).projectBinaries.get(
          projectId = Some(id),
          isSynced = Some(false),
          limit = 100
        )
        completedProjectBinaries <- dependencyClient(request).projectBinaries.get(
          projectId = Some(id),
          isSynced = Some(true),
          limit = 100
        )
      } yield {
        val actualN = if (n < 1) {
          1
        } else {
          n
        }

        val sleepTime = (actualN * 1.1).toInt match {
          case `actualN` => actualN + 1
          case other => other
        }

        val pending = pendingProjectLibraries.map { lib =>
          s"${lib.groupId}.${lib.artifactId}"
        } ++ pendingProjectBinaries.map {
          _.name
        }

        val completed = completedProjectLibraries.map { lib =>
          s"${lib.groupId}.${lib.artifactId}"
        } ++ completedProjectBinaries.map {
          _.name
        }

        syncs.find {
          _.event == SyncEvent.Completed
        } match {
          case Some(_) => {
            Redirect(routes.ProjectsController.show(id))
          }
          case None => {
            val syncStarted = syncs.find {
              _.event == SyncEvent.Started
            }
            if (!syncStarted.isEmpty && pending.isEmpty && !completed.isEmpty) {
              Redirect(routes.ProjectsController.show(id))
            } else if (n >= 10) {
              Redirect(routes.ProjectsController.show(id))
            } else {
              Ok(
                views.html.projects.sync(
                  uiData(request),
                  id,
                  actualN + 1,
                  sleepTime,
                  syncStarted,
                  pending,
                  completed
                )
              )
            }
          }
        }
      }
    }
  }

  def withProject[T](
    request: IdentifiedRequest[T],
    id: String
  )(
    f: Project => Future[Result]
  ) = {
    dependencyClient(request).projects.getById(id).flatMap { project =>
      f(project)
    }.recover {
      case UnitResponse(404) => {
        Redirect(routes.ProjectsController.index()).flashing("warning" -> s"Project not found")
      }
    }
  }

}

object ProjectsController {

  case class UiForm(
    organization: String,
    name: String,
    scms: String,
    visibility: String,
    uri: String,
    branch: String
  )

  private val uiForm = Form(
    mapping(
      "organization" -> nonEmptyText,
      "name" -> nonEmptyText,
      "scms" -> nonEmptyText,
      "visibility" -> nonEmptyText,
      "uri" -> nonEmptyText,
      "branch" -> nonEmptyText
    )(UiForm.apply)(UiForm.unapply)
  )

}
