package com.bryzek.dependency.api.lib

case class ArtifactVersion(
  tag: Version,
  crossBuildVersion: Option[Version]
)

