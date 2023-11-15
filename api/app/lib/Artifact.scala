package io.flow.dependency.api.lib

import db.generated.ProjectLibraryForm
import io.flow.dependency.v0.models.ProjectSummary
import io.flow.util.Version

case class Artifact(
  project: ProjectSummary,
  path: String,
  groupId: String,
  artifactId: String,
  version: String,
  isCrossBuilt: Boolean,
  isPlugin: Boolean,
) {

  def toProjectLibraryForm(
    crossBuildVersion: Option[Version],
  ): ProjectLibraryForm = {
    ProjectLibraryForm(
      organizationId = project.organization.id,
      projectId = project.id,
      path = path,
      groupId = groupId,
      artifactId = artifactId,
      version = version,
      crossBuildVersion = if (isCrossBuilt) {
        crossBuildVersion.map(_.value)
      } else {
        None
      },
      libraryId = None,
    )
  }

}
