package com.bryzek.dependency.www.lib

import com.bryzek.dependency.v0.models.{Organization, Project, Resolver, Visibility}
import io.flow.common.v0.models.User

object Permissions {

  object Organization {

    def edit(organization: Organization, user: Option[User]): Boolean = !user.isEmpty
    def delete(organization: Organization, user: Option[User]): Boolean = edit(organization, user)

  }

  object Project {

    def edit(project: Project, user: Option[User]): Boolean = !user.isEmpty
    def delete(project: Project, user: Option[User]): Boolean = edit(project, user)

  }

  object Resolver {

    def delete(resolver: Resolver, user: Option[User]): Boolean = {
      // TODO
      true
    }

  }

}
