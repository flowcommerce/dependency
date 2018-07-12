package io.flow.dependency.api.lib

import java.util.UUID

import io.flow.dependency.v0.models.{Library, OrganizationSummary}
import util.DependencySpec

class LibraryArtifactProviderSpec extends DependencySpec {

  def makeLibrary(
    org: OrganizationSummary,
    groupId: String = UUID.randomUUID.toString,
    artifactId: String = UUID.randomUUID.toString
  ): Library = {
    Library(
      id = UUID.randomUUID.toString,
      organization = orgSummary,
      groupId = groupId,
      artifactId = artifactId,
      resolver = makeResolverSummary()
    )
  }

  val provider: LibraryArtifactProvider = DefaultLibraryArtifactProvider
  lazy val orgSummary = OrganizationSummary(
    id = UUID.randomUUID.toString,
    key = s"z-test-${UUID.randomUUID.toString.toLowerCase}"
  )

  "parseUri" in {
    val library = makeLibrary(org = orgSummary, groupId = "com.github.tototoshi", artifactId = "scala-csv")
    val resolution = provider.resolve(resolversDao, organization = orgSummary, groupId = library.groupId, artifactId = library.artifactId).getOrElse {
      sys.error("Could not find scala-csv library")
    }
    resolution.versions.find { v =>
      v.tag.value == "1.2.2" && v.crossBuildVersion.map(_.value) == Some("2.11")
    }.map(_.tag.value) must be(Some("1.2.2"))
  }

  "swagger" in {
    val library = makeLibrary(org = orgSummary, groupId = "io.swagger", artifactId = "swagger-parser")
    val resolution = provider.resolve(resolversDao, organization = orgSummary, groupId = library.groupId, artifactId = library.artifactId).getOrElse {
      sys.error("Could not find swagger-parser library")
    }
    val tags = resolution.versions.map(_.tag.value)
    tags.contains("1.0.4") must be(true)
    tags.contains("1.0.13") must be(true)
    tags.contains("0.0.139") must be(false)
  }

}
