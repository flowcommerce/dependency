package io.flow.dependency.api.lib

import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._

import io.flow.dependency.v0.models.{Library, OrganizationSummary}
import org.joda.time.DateTime
import java.util.UUID

class LibraryArtifactProviderSpec extends PlaySpec with OneAppPerSuite with Factories {

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

  lazy val provider = DefaultLibraryArtifactProvider()
  lazy val orgSummary = OrganizationSummary(
    id = UUID.randomUUID.toString,
    key = s"z-test-${UUID.randomUUID.toString.toLowerCase}"
  )

  "parseUri" in {
    val library = makeLibrary(org = orgSummary, groupId = "com.github.tototoshi", artifactId = "scala-csv")
    val resolution = provider.resolve(organization = orgSummary, groupId = library.groupId, artifactId = library.artifactId).getOrElse {
      sys.error("Could not find scala-csv library")
    }
    resolution.versions.find { v =>
      v.tag.value == "1.2.2" && v.crossBuildVersion.map(_.value) == Some("2.11")
    }.map(_.tag.value) must be(Some("1.2.2"))
  }

  "swagger" in {
    val library = makeLibrary(org = orgSummary, groupId = "io.swagger", artifactId = "swagger-parser")
    val resolution = provider.resolve(organization = orgSummary, groupId = library.groupId, artifactId = library.artifactId).getOrElse {
      sys.error("Could not find swagger-parser library")
    }
    val tags = resolution.versions.map(_.tag.value)
    tags.contains("1.0.4") must be(true)
    tags.contains("1.0.13") must be(true)
    tags.contains("0.0.139") must be(false)
  }

}
