package db

import io.flow.dependency.v0.models.{Organization, Recommendation}
import util.DependencySpec

import scala.util.Try

class RecommendationsDaoSpec extends DependencySpec{

  def createRecommendation(
    org: Organization
  ): Recommendation = {
    val (_, libraryVersions) = createLibraryWithMultipleVersions(org)
    val project = createProject(org)
    addLibraryVersion(project, libraryVersions.head)

    def getFirst() = {
      val projectRecommendations = recommendationsDao.findAll(Authorization.All, projectId = Some(project.id))

      projectRecommendations.headOption.getOrElse {
        sys.error("Failed to create recommendation")
      }
    }

    Try {
      eventuallyInNSeconds(3) {
        getFirst()
      }
    }.getOrElse {
      recommendationsDao.sync(systemUser, project)
      getFirst()
    }
  }

  private[this] lazy val org = createOrganization()

  "delete" in {
    val rec = createRecommendation(org)
    recommendationsDao.delete(systemUser, rec)
    recommendationsDao.findAll(Authorization.All, projectId = Some(rec.project.id)) must be(Nil)
  }

  "no-op if nothing to upgrade" in {
    val project = createProject(org)()
    recommendationsDao.sync(systemUser, project)
    recommendationsDao.findAll(Authorization.All, projectId = Some(project.id)) must be(Nil)
  }


  "ignores earlier versions of library" in {
    val (_, libraryVersions) = createLibraryWithMultipleVersions(org)
    val project = createProject(org)()
    addLibraryVersion(project, libraryVersions.last)
    recommendationsDao.sync(systemUser, project)
    recommendationsDao.findAll(Authorization.All, projectId = Some(project.id)) must be(Nil)
  }

  "with library to upgrade" in {
    val rec = createRecommendation(org)

    recommendationsDao.findAll(Authorization.All, projectId = Some(rec.project.id)).map(rec => (rec.from, rec.to)) must be(
      Seq(
        ("1.0.0", "1.0.2")
      )
    )

    recommendationsDao.findAll(
      Authorization.All,
      organization = Some(rec.project.organization.key),
      projectId = Some(rec.project.id)
    ).map(rec => (rec.from, rec.to)) must be(
      Seq(
        ("1.0.0", "1.0.2")
      )
    )

    recommendationsDao.findAll(
      Authorization.All,
      organization = Some(createOrganization().key),
      projectId = Some(rec.project.id)
    ) must be(Nil)
  }

  "Prefers latest production release even when more recent beta release is available" in {
    val (library, libraryVersions) = createLibraryWithMultipleVersions(org,
      versions = Seq("1.0.0", "1.0.2-RC1", "1.0.1")
    )
    val project = createProject(org)()
    addLibraryVersion(project, libraryVersions.head)
    libraryRecommendationsDao.forProject(project) must be(
      Seq(
        LibraryRecommendation(
          library = library,
          from = "1.0.0",
          to = libraryVersions.find(_.version == "1.0.1").get,
          latest = libraryVersions.find(_.version == "1.0.2-RC1").get
        )
      )
    )
  }

}
