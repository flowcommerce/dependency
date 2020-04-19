package services

/**
 * Captures assumptions on matching artifact names to the projects
 * that product them.
 */
object ArtifactNameMatcher {

  // will match "xxx-akka" and "xxx-akka26"
  private[this] val KnownSuffixes = Seq(
    "akka",
    "play",
  ).map(format)

  def matches(artifactId: String, projectName: String): Boolean = {
    val fArtifactId = format(artifactId)
    val fProjectName = format(projectName)
    if (fArtifactId == fProjectName) {
      true
    } else if (fArtifactId.startsWith(fProjectName + "-")) {
      val suffix = fArtifactId.drop(fProjectName.length + 1)
      KnownSuffixes.exists { s =>
        if (suffix.startsWith(s)) {
          val remaining = suffix.drop(s.length)
          remaining.isEmpty || remaining.forall(_.isDigit)
        } else {
          false
        }
      }
    } else {
      false
    }
  }

  private[this] def format(str: String): String = str.trim.toLowerCase()
}
