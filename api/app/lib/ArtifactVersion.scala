package io.flow.dependency.api.lib

case class ArtifactVersion(
  tag: Version,
  crossBuildVersion: Option[Version]
)

