package io.flow.dependency.api.lib


import io.flow.dependency.v0.models.{LibraryForm, ProjectSummary}

trait SimpleScalaParser {

  def project: ProjectSummary

  /**
    * Full path to the file from this project's repository that we
    * parsed. Useful for the user to identify from where the actual
    * dependency came.
    */
  def path: String

  def contents: String

  lazy val lines = parseIntoLines(contents)

  /**
    * Parses into meaningful lines of data, stripping comments and
    * removing blank lines
    */
  def parseIntoLines(contents: String): Seq[String] = {
    contents.
      split("\n").
      map(stripComments(_)).
      map(_.trim).
      filter(!_.isEmpty)
  }

  // Pull out all lines that start w/ "val " or "var " and capture
  // variable declarations
  lazy val variables: Seq[SimpleScalaParserUtil.Variable] = SimpleScalaParserUtil.parseVariables(lines)

  /**
    * This method will substitute any variables for their values. For
    * literals, we strip the quotes.
    */
  def interpolate(value: String): String = {
    variables.find(_.name == value) match {
      case None => SimpleScalaParserUtil.cleanVariable(value)
      case Some(variable) => variable.value
    }
  }

  /**
    * Removes any in-line comments - handles both block and trailing // comments.
    * 
    * Taken from http://stackoverflow.com/questions/1657066/java-regular-expression-finding-comments-in-code
    */
  def stripComments(value: String): String = {
    value.replaceAll( "//.*|(\"(?:\\\\[^\"]|\\\\\"|.)*?\")|(?s)/\\*.*?\\*/", "$1 " ).trim
  }


  def parseLibraries(): Seq[Artifact] = {
    lines.
      filter(_.replaceAll("%%", "%").split("%").size >= 2).
      filter(!_.startsWith(".dependsOn")).
      map(_.stripSuffix(",")).
      map(_.trim).
      map {
        toArtifacts(_)
      }.flatten.distinct.sortBy { l => (l.groupId, l.artifactId, l.version) }
  }

  def toArtifacts(value: String): Seq[Artifact] = {
    val firstParen = value.indexOf("(")
    val lastParen = value.lastIndexOf(")")

    val substring = if (firstParen >= 0) {
      value.substring(firstParen+1, lastParen)
    } else {
      value
    }

    substring.split(",").map(_.trim).filter(!_.isEmpty).flatMap { el =>
      el.replaceAll("%%", "%").split("%").map(_.trim).toList match {
        case Nil => {
          warn(s"Could not parse library from[$value]")
          None
        }
        case groupId :: Nil => {
          warn(s"Could not parse library from[$value] - only found groupId[$groupId] but missing artifactId and version")
          None
        }
        case groupId :: artifactId :: Nil => {
          warn(s"Could not parse library from[$value] - only found groupId[$groupId] and artifactId[$artifactId] but missing version")
          None
        }
        case groupId :: artifactId :: version :: more => {
          Some(
            Artifact(
              project = project,
              path = path,
              groupId = interpolate(groupId),
              artifactId = interpolate(artifactId),
              version = interpolate(version),
              isCrossBuilt = (el.indexOf("%%") >= 0)
            )
          )
        }
      }
    }
  }

  private[this] def warn(message: String) {
    Logger.warn(s"project[${project.id}] name[${project.name}] path[$path]: $message")
  }

}

object SimpleScalaParserUtil {

  case class Variable(name: String, value: String)

  def parseVariables(lines: Seq[String]): Seq[Variable] = {
    lines.flatMap(toVariable(_))
  }

  def toVariable(value: String): Option[Variable] = {
    val trimmed = value.trim
    (trimmed.startsWith("val ") || trimmed.startsWith("var ")) match {
      case true => {
        trimmed.substring(4).trim.split("=").map(_.trim).toList match {
          case declaration :: value :: Nil => {
            Some(Variable(declaration, cleanVariable(value)))
          }
          case _ => {
            None
          }
        }
      }
      case false => {
        trimmed.startsWith("lazy ") match {
          case false => None
          case true => toVariable(trimmed.substring(5))
        }
      }
    }
  }

  @scala.annotation.tailrec
  final def cleanVariable(value: String): String = {
    val stripped = stripQuotes(value)
    (stripped == value) match {
      case false => cleanVariable(stripped)
      case true => {
        val stripped2 = stripTrailingCommas(value)
          (stripped2 == value) match {
          case false => cleanVariable(stripped2)
          case true => value
        }
      }
    }
  }

  /**
   * Removes leading and trailing quotes
   */
  def stripQuotes(value: String): String = {
    value.stripPrefix("\"").stripSuffix("\"").trim
  }

  /**
   * Removes leading and trailing quotes
   */
  def stripTrailingCommas(value: String): String = {
    value.stripSuffix(",").trim
  }


}
