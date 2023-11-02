package db

import anorm.SQL
import io.flow.common.v0.models.UserReference
import play.api.db._

case class DbHelpers(db: Database, tableName: String) {

  private[this] val Query = """
    select util.delete_by_id({updated_by_user_id}, '%s', {id})
  """

  def delete(deletedById: String, id: String): Unit = {
    delete(UserReference(deletedById), id)
  }

  def delete(implicit
    c: java.sql.Connection,
    deletedById: String,
    id: String
  ): Unit = {
    delete(c, UserReference(deletedById), id)
  }

  def delete(deletedBy: UserReference, id: String): Unit = {
    db.withConnection { implicit c =>
      delete(c, deletedBy, id)
    }
  }

  def delete(implicit
    c: java.sql.Connection,
    deletedBy: UserReference,
    id: String
  ): Unit = {
    SQL(Query.format(tableName))
      .on(
        "id" -> id,
        "updated_by_user_id" -> deletedBy.id
      )
      .execute()
    ()
  }

}
