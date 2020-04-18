package helpers.resolver

import io.flow.test.utils.FlowPlaySpec
import lib.resolver.{LibraryReference, ProjectInfo}

trait ResolverHelpers {
  this: FlowPlaySpec =>

  def makeLibraryReference(libraryId: String = createTestId()): LibraryReference = {
    LibraryReference(libraryId = libraryId)
  }

  def makeProjectInfo(
    projectId: String = createTestId(),
    dependsOn: Seq[LibraryReference] = Nil,
    provides: Seq[LibraryReference] = Nil,
  ): ProjectInfo = {
    ProjectInfo(
      projectId = projectId,
      dependsOn = dependsOn,
      provides = provides,
    )
  }
}
