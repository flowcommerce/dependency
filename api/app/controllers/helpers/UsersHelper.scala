package controllers.helpers

import javax.inject.{Inject, Singleton}

import db.UsersDao
import io.flow.common.v0.models.User
import play.api.mvc.{Result, Results}

@Singleton
class UsersHelper @Inject() (
  usersDao: UsersDao
) {

  def withUser(id: String)(
    f: User => Result
  ) = {
    usersDao.findById(id) match {
      case None => {
        Results.NotFound
      }
      case Some(user) => {
        f(user)
      }
    }
  }

}
