package dependency.resolver

import io.flow.util.Random

trait ResolverHelpers {
  private[this] val random: Random = Random()
  private[this] def createTestId(): String = "tst-" + random.alphaNumeric(36)

  def makeProjectInfo(
    projectId: String = createTestId(),
    dependsOn: Seq[String] = Nil,
    provides: Seq[String] = Nil,
  ): ProjectInfo = {
    ProjectInfo(
      projectId = projectId,
      dependsOn = dependsOn.map(LibraryReference),
      provides = provides.map(LibraryReference),
    )
  }
}
