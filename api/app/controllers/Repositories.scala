package controllers

import db.{Authorization, OrganizationsDao, ProjectsDao}
import io.flow.dependency.api.lib.Github
import io.flow.error.v0.models.json._
import io.flow.github.v0.models.json._
import io.flow.play.controllers.FlowControllerComponents
import io.flow.play.util.{Config, Validation}
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class Repositories @javax.inject.Inject() (
  val github: Github,
  val config: Config,
  val controllerComponents: ControllerComponents,
  val flowControllerComponents: FlowControllerComponents,
  projectsDao: ProjectsDao,
  organizationsDao: OrganizationsDao,
  val baseIdentifiedControllerWithFallbackComponents: BaseIdentifiedControllerWithFallbackComponents
)(implicit val ec: ExecutionContext) extends BaseIdentifiedControllerWithFallback {

  def getGithub(
    owner: Option[String] = None, // Ex: flowcommerce
    name: Option[String] = None,
    organizationId: Option[String] = None,
    existingProject: Option[Boolean] = None,
    limit: Long = 25,
    offset: Long = 0
  ) = IdentifiedWithFallback.async { request =>
    if (!existingProject.isEmpty && organizationId.isEmpty) {
      Future {
        UnprocessableEntity(Json.toJson(Validation.error("When filtering by existing projects, you must also provide the organization_id")))
      }
    } else {
      val auth = Authorization.User(request.user.id)
      val org = organizationId.flatMap { organizationsDao.findById(auth, _)}

      // Set limit to 1 if we are guaranteed at most 1 record back
      val actualLimit = if (offset == 0 && !name.isEmpty && !owner.isEmpty) { 1 } else { limit }

      github.repositories(request.user, offset, actualLimit) { r =>
        (name match {
          case None => true
          case Some(n) => n.toLowerCase == r.name.toLowerCase
        }) &&
        (owner match {
          case None => true
          case Some(o) => o.toLowerCase == r.owner.login.toLowerCase
        }) &&
        (org match {
          case None => true
          case Some(org) => {
            existingProject.isEmpty ||
            existingProject == Some(true) && !projectsDao.findByOrganizationKeyAndName(auth, org.id, r.name).isEmpty ||
            existingProject == Some(false) && projectsDao.findByOrganizationKeyAndName(auth, org.id, r.name).isEmpty
          }
        })
      }.map { results =>
        Ok(Json.toJson(results))
      }
    }
  }
}
