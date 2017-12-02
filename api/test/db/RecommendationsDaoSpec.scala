package db

import com.bryzek.dependency.v0.models.{Organization, Recommendation}
import org.scalatestplus.play._

class RecommendationsDaoSpec extends PlaySpec with OneAppPerSuite with Helpers {

  def createRecommendation(
    org: Organization
  ): Recommendation = {
    val (_,  libraryVersions) = createLibraryWithMultipleVersions(org)
    val project = createProject(org)
    addLibraryVersion(project, libraryVersions.head)
    RecommendationsDao.sync(systemUser, project)
    RecommendationsDao.findAll(Authorization.All, projectId = Some(project.id)).headOption.getOrElse {
      sys.error("Failed to create recommendation")
    }
  }

  private[this] lazy val org = createOrganization()

  "delete" in {
    val rec = createRecommendation(org)
    RecommendationsDao.delete(systemUser, rec)
    RecommendationsDao.findAll(Authorization.All, projectId = Some(rec.project.id)) must be(Nil)
  }

  "no-op if nothing to upgrade" in {
    val project = createProject(org)()
    RecommendationsDao.sync(systemUser, project)
    RecommendationsDao.findAll(Authorization.All, projectId = Some(project.id)) must be(Nil)
  }


  "ignores earlier versions of library" in {
    val (_,  libraryVersions) = createLibraryWithMultipleVersions(org)
    val project = createProject(org)()
    addLibraryVersion(project, libraryVersions.last)
    RecommendationsDao.sync(systemUser, project)
    RecommendationsDao.findAll(Authorization.All, projectId = Some(project.id)) must be(Nil)
  }

  "with library to upgrade" in {
    val rec = createRecommendation(org)

    RecommendationsDao.findAll(Authorization.All, projectId = Some(rec.project.id)).map(rec => (rec.from, rec.to)) must be(
      Seq(
        ("1.0.0", "1.0.2")
      )
    )

    RecommendationsDao.findAll(
      Authorization.All,
      organization = Some(rec.project.organization.key),
      projectId = Some(rec.project.id)
    ).map(rec => (rec.from, rec.to)) must be(
      Seq(
        ("1.0.0", "1.0.2")
      )
    )

    RecommendationsDao.findAll(
      Authorization.All,
      organization = Some(createOrganization().key),
      projectId = Some(rec.project.id)
    ) must be(Nil)
  }

  "Prefers latest production release even when more recent beta release is available" in {
    val (library,  libraryVersions) = createLibraryWithMultipleVersions(org)(
      versions = Seq("1.0.0", "1.0.2-RC1", "1.0.1")
    )
    val project = createProject(org)()
    addLibraryVersion(project, libraryVersions.head)
    LibraryRecommendationsDao.forProject(project) must be(
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
