package controllers.helpers

import javax.inject.{Inject, Singleton}

import db.{Authorization, OrganizationsDao, ProjectsDao}
import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.models.{Organization, Project}
import play.api.mvc.{Result, Results}

@Singleton
class OrganizationsHelper @Inject()(
  organizationsDao: OrganizationsDao
) {

  def withOrganization(user: UserReference, id: String)(
    f: Organization => Result
  ) = {
    organizationsDao.findById(Authorization.User(user.id), id) match {
      case None => {
        Results.NotFound
      }
      case Some(organization) => {
        f(organization)
      }
    }
  }

}
