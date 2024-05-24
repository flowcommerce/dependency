package io.flow.dependency.www.lib

import scala.annotation.nowarn
import io.flow.dependency.v0.models.{Organization, Project, Resolver}
import io.flow.common.v0.models.User

object Permissions {

  object Organization {

    @nowarn
    def edit(organization: Organization, user: Option[User]): Boolean = !user.isEmpty
    def delete(organization: Organization, user: Option[User]): Boolean = edit(organization, user)

  }

  object Project {

    @nowarn
    def edit(project: Project, user: Option[User]): Boolean = !user.isEmpty
    def delete(project: Project, user: Option[User]): Boolean = edit(project, user)

  }

  object Resolver {

    // @nowarn
    def delete(resolver: Resolver, user: Option[User]): Boolean = {
      // TODO
      true
    }

  }

}
