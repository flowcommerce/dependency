package com.bryzek.dependency.api.lib

import db.ProjectLibraryForm
import com.bryzek.dependency.v0.models.{LibraryForm, ProjectSummary, VersionForm}

case class Artifact(
  project: ProjectSummary,
  path: String,
  groupId: String,
  artifactId: String,
  version: String,
  isCrossBuilt: Boolean
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
