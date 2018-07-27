package io.flow.dependency.api.lib

import io.flow.util.Version

case class ArtifactVersion(
  tag: Version,
  crossBuildVersion: Option[Version]
)

