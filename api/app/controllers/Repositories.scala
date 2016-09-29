package controllers

import db.{Authorization, OrganizationsDao, ProjectsDao}
import com.bryzek.dependency.api.lib.Github
import com.bryzek.dependency.v0.models.json._
import io.flow.common.v0.models.json._
import io.flow.github.v0.models.json._
import io.flow.play.controllers.IdentifiedRestController
import io.flow.play.util.Validation
import play.api.mvc._
import play.api.libs.json._
import scala.concurrent.Future

class Repositories @javax.inject.Inject() (
  override val config: io.flow.play.util.Config,
  override val tokenClient: io.flow.token.v0.interfaces.Client,
  val github: Github
) extends Controller with IdentifiedRestController {

  import scala.concurrent.ExecutionContext.Implicits.global

  def getGithub(
    owner: Option[String] = None, // Ex: flowcommerce
    name: Option[String] = None,
    organizationId: Option[String] = None,
    existingProject: Option[Boolean] = None,
    limit: Long = 25,
    offset: Long = 0
  ) = Identified.async { request =>
    println(s"getGithub")
    println(s"  - owner: $owner")
    println(s"  - name: $name")
    println(s"  - organizationId: $organizationId")
    println(s"  - existingProject: $existingProject")


    if (!existingProject.isEmpty && organizationId.isEmpty) {
      Future {
        UnprocessableEntity(Json.toJson(Validation.error("When filtering by existing projects, you must also provide the organization_id")))
      }
    } else {
      val auth = Authorization.User(request.user.id)
      val org = organizationId.flatMap { OrganizationsDao.findById(auth, _)}

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
            existingProject == Some(true) && !ProjectsDao.findByOrganizationKeyAndName(auth, org.id, r.name).isEmpty ||
            existingProject == Some(false) && ProjectsDao.findByOrganizationKeyAndName(auth, org.id, r.name).isEmpty
          }
        })
      }.map { results =>
        Ok(Json.toJson(results))
      }
    }
  }
}
