package io.flow.dependency.api.lib


import io.flow.dependency.v0.models.ProjectSummary
import io.flow.log.RollbarLogger

trait SimpleScalaParser {

  val logger: RollbarLogger

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
    val moduleIdRegex = """"([^"]+)"\s*(%{1,3})\s*s?"([^"]+)"\s*%\s*("?[_\w.-]+"?)""".r

    lines.flatMap { line =>
      moduleIdRegex.findAllMatchIn(line).map { regexMatch =>
        val groupId :: crossBuilt :: artifactId :: version :: Nil = regexMatch.subgroups

        Artifact(
          project = project,
          path = path,
          groupId = groupId,
          artifactId = replaceVariables(artifactId, lines),
          version = if (version.startsWith("\"")) SimpleScalaParserUtil.stripQuotes(version) else interpolate(version),
          isCrossBuilt = crossBuilt.length > 1
        )
      }
    }.distinct.sortBy { l => (l.groupId, l.artifactId, l.version) }.toList
  }

  private def replaceVariables(str: String, lines: Seq[String]): String = {
    val StrInterpolRx = """([^\$]*)\$\{?([\w]+)\}?(.*)""".r
    val ValRx = """\s*[\w]+\s*([\w]+)\s*=\s*"([^"]*)"\s*""".r

    def findVal(toFind: String): Option[String] = lines.collect {
      case ValRx(name, value) if name == toFind => value
    }.headOption

    str match {
      case StrInterpolRx(prefix, interpol, suffix) =>
        findVal(interpol).fold(str)(value => replaceVariables(s"$prefix$value$suffix", lines))
      case _ =>
        str
    }
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
