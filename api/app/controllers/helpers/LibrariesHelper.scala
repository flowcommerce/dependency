package controllers.helpers

import javax.inject.{Inject, Singleton}

import db.{Authorization, LibrariesDao}
import io.flow.common.v0.models.{User, UserReference}
import io.flow.dependency.v0.models.{Library, Organization, Project, Resolver}
import play.api.mvc.{Result, Results}

@Singleton
class LibrariesHelper @Inject()(
  librariesDao: LibrariesDao
) {

  def withLibrary(user: UserReference, id: String)(
    f: Library => Result
  ): Result = {
    librariesDao.findById(Authorization.User(user.id), id) match {
      case None => {
        Results.NotFound
      }
      case Some(library) => {
        f(library)
      }
    }
  }


}
