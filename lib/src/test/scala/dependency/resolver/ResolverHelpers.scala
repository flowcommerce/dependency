package dependency.resolver

import io.flow.util.Random

trait ResolverHelpers {
  private[this] val random: Random = Random()
  private[this] def createTestId(): String = "tst-" + random.alphaNumeric(36)

  def makeLibraryReference(
    groupId: String = createTestId(),
    artifactId: String = createTestId(),
  ): LibraryReference = {
    LibraryReference(groupId = groupId, artifactId = artifactId)
  }

  def makeProjectInfo(
    projectId: String = createTestId(),
    dependsOn: Seq[LibraryReference] = Nil,
    provides: Seq[LibraryReference] = Nil,
  ): ProjectInfo = {
    ProjectInfo(
      projectId = projectId,
      projectName = projectId.split("\\.").last, // eg. io.flow.lib-s3 becomes lib-s3
      dependsOn = dependsOn,
      provides = provides,
    )
  }
}
