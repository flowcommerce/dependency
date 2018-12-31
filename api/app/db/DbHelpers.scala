package db

import javax.inject.{Inject, Singleton}

import anorm._
import play.api.db._

@Singleton
class DbHelpers @Inject()(
  db: Database
){

  private[this] val Query = """
    select util.delete_by_id({updated_by_user_id}, '%s', {id})
  """

  def delete(tableName: String, deletedById: String, id: String): Unit = {
    db.withConnection { implicit c =>
      delete(c, tableName, deletedById, id)
    }
  }

  def delete(
    implicit c: java.sql.Connection,
    tableName: String, deletedById: String, id: String
  ): Unit = {
    SQL(Query.format(tableName)).on(
      'id -> id,
      'updated_by_user_id -> deletedById
    ).execute()
    ()
  }

}
