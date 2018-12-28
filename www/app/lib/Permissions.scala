package io.flow.dependency.www.lib

import com.github.ghik.silencer.silent
import io.flow.dependency.v0.models.{Organization, Project, Resolver}
import io.flow.common.v0.models.User

object Permissions {

  object Organization {

    @silent
    def edit(organization: Organization, user: Option[User]): Boolean = !user.isEmpty
    def delete(organization: Organization, user: Option[User]): Boolean = edit(organization, user)

  }

  object Project {

    @silent
    def edit(project: Project, user: Option[User]): Boolean = !user.isEmpty
    def delete(project: Project, user: Option[User]): Boolean = edit(project, user)

  }

  object Resolver {

    @silent
    def delete(resolver: Resolver, user: Option[User]): Boolean = {
      // TODO
      true
    }

  }

}
