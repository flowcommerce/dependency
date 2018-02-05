package controllers.helpers

import javax.inject.{Inject, Singleton}

import db.{Authorization, BinariesDao, LibrariesDao}
import io.flow.common.v0.models.UserReference
import io.flow.dependency.v0.models.{Binary, Library}
import play.api.mvc.{Result, Results}

@Singleton
class BinaryHelper @Inject()(
  binariesDao: BinariesDao
) {

  def withBinary(user: UserReference, id: String)(
    f: Binary => Result
  ): Result = {
    binariesDao.findById(Authorization.User(user.id), id) match {
      case None => {
        Results.NotFound
      }
      case Some(binary) => {
        f(binary)
      }
    }
  }


}
