package controllers.helpers

import javax.inject.{Inject, Singleton}

import db.BinariesDao
import io.flow.dependency.v0.models.Binary
import play.api.mvc.{Result, Results}

@Singleton
class BinaryHelper @Inject() (
  binariesDao: BinariesDao,
) {

  def withBinary(id: String)(
    f: Binary => Result,
  ): Result = {
    binariesDao.findById(id) match {
      case None => {
        Results.NotFound
      }
      case Some(binary) => {
        f(binary)
      }
    }
  }

}
