package io.flow.dependency.api.lib

import db.ProjectLibraryForm
import io.flow.dependency.v0.models.{ProjectSummary, VersionForm}
import io.flow.util.Version

case class Artifact(
  project: ProjectSummary,
  path: String,
  groupId: String,
  artifactId: String,
  version: String,
  isCrossBuilt: Boolean,
  isPlugin: Boolean = false,
) {

  def toProjectLibraryForm(
    crossBuildVersion: Option[Version]
  ): ProjectLibraryForm = {
    ProjectLibraryForm(
      projectId = project.id,
      path = path,
      groupId = groupId,
      artifactId = artifactId,
      version = VersionForm(
        version = version,
        crossBuildVersion = isCrossBuilt match {
          case true => crossBuildVersion.map(_.value)
          case false => None
        }
      )
    )
  }

}
