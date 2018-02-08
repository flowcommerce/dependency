package controllers

import controllers.helpers.ProjectHelper
import db.{Authorization, ProjectsDao}
import io.flow.dependency.v0.models.json._
import io.flow.dependency.v0.models.{ProjectForm, ProjectPatchForm}
import io.flow.error.v0.models.json._
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.{Config, Validation}
import play.api.libs.json._
import play.api.mvc._

@javax.inject.Singleton
class Projects @javax.inject.Inject() (
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  projectsDao: ProjectsDao,
  projectHelper: ProjectHelper,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents
) extends BaseIdentifiedControllerWithFallback {

  def get(
    id: Option[String],
    ids: Option[Seq[String]],
    organization: Option[String],
    name: Option[String],
    groupId: _root_.scala.Option[String],
    artifactId: _root_.scala.Option[String],
    version: _root_.scala.Option[String],
    libraryId: _root_.scala.Option[String],
    binary: _root_.scala.Option[String],
    binaryId: _root_.scala.Option[String],
    limit: Long = 25,
    offset: Long = 0
  ) = IdentifiedWithFallback { request =>
    Ok(
      Json.toJson(
        projectsDao.findAll(
          Authorization.User(request.user.id),
          id = id,
          ids = optionals(ids),
          name = name,
          organizationKey = organization,
          groupId = groupId,
          artifactId = artifactId,
          version = version,
          libraryId = libraryId,
          binary = binary,
          binaryId = binaryId,
          limit = limit,
          offset = offset
        )
      )
    )
  }

  def getById(id: String) = IdentifiedWithFallback { request =>
    projectHelper.withProject(request.user, id) { project =>
      Ok(Json.toJson(project))
    }
  }

  def post() = IdentifiedWithFallback(parse.json) { request =>
    request.body.validate[ProjectForm] match {
      case e: JsError => {
        UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
      }
      case s: JsSuccess[ProjectForm] => {
        projectsDao.create(request.user, s.get) match {
          case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
          case Right(project) => Created(Json.toJson(project))
        }
      }
    }
  }

  def patchById(id: String) = IdentifiedWithFallback(parse.json) { request =>
    projectHelper.withProject(request.user, id) { project =>
      request.body.validate[ProjectPatchForm] match {
        case e: JsError => {
          UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
        }
        case s: JsSuccess[ProjectPatchForm] => {
          val patch = s.get
          val form = ProjectForm(
            organization = project.organization.key,  // do not support patch to move project to a new org
            name = patch.name.getOrElse(project.name),
            visibility = patch.visibility.getOrElse(project.visibility),
            scms = patch.scms.getOrElse(project.scms),
            uri = patch.uri.getOrElse(project.uri)
          )
          projectsDao.update(request.user, project, form) match {
            case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
            case Right(updated) => Ok(Json.toJson(updated))
          }
        }
      }
    }
  }

  def putById(id: String) = IdentifiedWithFallback(parse.json) { request =>
    projectHelper.withProject(request.user, id) { project =>
      request.body.validate[ProjectForm] match {
        case e: JsError => {
          UnprocessableEntity(Json.toJson(Validation.invalidJson(e)))
        }
        case s: JsSuccess[ProjectForm] => {
          projectsDao.update(request.user, project, s.get) match {
            case Left(errors) => UnprocessableEntity(Json.toJson(Validation.errors(errors)))
            case Right(updated) => Ok(Json.toJson(updated))
          }
        }
      }
    }
  }

  def deleteById(id: String) = IdentifiedWithFallback { request =>
    projectHelper.withProject(request.user, id) { project =>
      projectsDao.delete(request.user, project)
      NoContent
    }
  }

}
