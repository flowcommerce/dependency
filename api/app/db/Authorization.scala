package db

import io.flow.dependency.v0.models.Visibility

trait Clause {

  def sql: String

  def and: String = s"and $sql"

}

object Clause {

  case object True extends Clause {
    override val sql: String = "true"
  }

  case object False extends Clause {
    override val sql: String = "false"
  }

  case class Or(conditions: Seq[String]) extends Clause {
    assert(conditions.nonEmpty, "Must have at least one condition")

    override val sql: String = conditions match {
      case Nil => "false"
      case one :: Nil => one
      case inple => "(" + inple.mkString(" or ") + ")"
    }

  }

  def single(condition: String): Clause = {
    assert(!condition.trim.isEmpty, "condition cannot be empty")
    Or(Seq(condition))
  }

}

sealed trait Authorization {

  private[this] val IdRegex = """^[\w\d\-\_]+$""".r

  private[db] def assertValidId(id: String): Unit = {
    id match {
      case IdRegex() => {}
      case _ => sys.error(s"Invalid id[$id]")
    }
  }

  def organizationProjects(
    organizationIdColumn: String,
    projectIdColumn: String,
  ): Clause

  def organizations(
    organizationIdColumn: String,
    visibilityColumnName: Option[String] = None
  ): Clause

  def users(
    userIdColumn: String
  ): Clause

}


object Authorization {

  private[this] def publicVisibilityClause(column: String) = {
    s"$column = '${Visibility.Public}'"
  }

  private[this] def publicProjectsClause(projectIdColumn: String) = {
    s"${projectIdColumn} in (select id from projects where ${publicVisibilityClause("visibility")})"
  }

  case object PublicOnly extends Authorization {

    override def organizations(
      organizationIdColumn: String,
      visibilityColumnName: Option[String] = None
    ): Clause = {
      visibilityColumnName match {
        case None => Clause.False
        case Some(col) => Clause.single(publicVisibilityClause(col))
      }
    }

    override def organizationProjects(
      organizationIdColumn: String,
      projectIdColumn: String,
    ): Clause = {
      Clause.single(publicProjectsClause(projectIdColumn))
    }

    override def users(
      userIdColumn: String
    ) = Clause.False

  }

  case object All extends Authorization {

    override def organizations(
      organizationIdColumn: String,
      visibilityColumnName: Option[String] = None
    ): Clause = Clause.True

    override def organizationProjects(
      organizationIdColumn: String,
      projectIdColumn: String,
    ): Clause = Clause.True

    override def users(
      userIdColumn: String
    ) = Clause.True

  }

  case class User(id: String) extends Authorization {
    assertValidId(id)

    override def organizations(
      organizationIdColumn: String,
      visibilityColumnName: Option[String] = None
    ): Clause = {
      // TODO: Bind
      val userClause = s"$organizationIdColumn in (select organization_id from memberships where user_id = '$id')"
      visibilityColumnName match {
        case None => Clause.single(userClause)
        case Some(col) => Clause.Or(Seq(userClause, publicVisibilityClause(col)))
      }
    }

    override def organizationProjects(
      organizationIdColumn: String,
      projectIdColumn: String,
    ): Clause = {
      // TODO: Bind
      val userClause = s"$organizationIdColumn in (select organization_id from memberships where user_id = '$id')"
      Clause.Or(Seq(userClause, publicProjectsClause(projectIdColumn)))
    }

    override def users(
      userIdColumn: String
    ) = Clause.single(s"$userIdColumn = '$id'")

  }

  case class Organization(id: String) extends Authorization {
    assertValidId(id)

    override def organizations(
      organizationIdColumn: String,
      visibilityColumnName: Option[String] = None
    ): Clause = {
      val orgClause = s"$organizationIdColumn = '$id'"
      visibilityColumnName match {
        case None => Clause.single(orgClause)
        case Some(col) => Clause.Or(Seq(orgClause, publicVisibilityClause(col)))
      }
    }

    override def organizationProjects(
      organizationIdColumn: String,
      projectIdColumn: String,
    ): Clause = {
      // TODO: Bind
      val orgClause = s"$organizationIdColumn = '$id'"
      Clause.Or(Seq(orgClause, publicProjectsClause(projectIdColumn)))
    }

    override def users(
      userIdColumn: String
    ) = Clause.single(s"$userIdColumn in (select user_id from memberships where organization_id = '$id')")

  }

  def fromUser(userId: Option[String]): Authorization = {
    userId match {
      case None => Authorization.PublicOnly
      case Some(id) => Authorization.User(id)
    }
  }

  def fromOrganization(orgId: Option[String]): Authorization = {
    orgId match {
      case None => Authorization.PublicOnly
      case Some(id) => Authorization.Organization(id)
    }
  }

}
