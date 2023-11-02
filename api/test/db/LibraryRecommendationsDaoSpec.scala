package db

import io.flow.util.Constants
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.time.{Millis, Seconds, Span}
import util.DependencySpec

class LibraryRecommendationsDaoSpec extends DependencySpec with Eventually with IntegrationPatience {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(60, Seconds)),
    interval = scaled(Span(250, Millis))
  )

  private[this] lazy val org = createOrganization()

  def verify(actual: Seq[LibraryRecommendation], expected: Seq[LibraryRecommendation]): Unit = {
    if (actual == expected) {
      {}
    } else {
      if (actual.size == expected.size) {
        (actual zip expected).foreach { case (a, b) =>
          if (a == b) {
            {}
          } else {
            sys.error(
              s"Expected[${b.from} => ${b.to.version}] but got[${a.from} => ${a.to.version}]. For latest version, expected[${b.latest.version}] but got[${a.latest.version}]"
            )
          }
        }
      } else {
        sys.error(s"Expected[${expected.size}] recommendations but got [${actual.size}]")
      }
    }
  }

  "no-op if nothing to upgrade" in {
    val project = createProject(org)
    libraryRecommendationsDao.forProject(project) must be(Nil)
  }

  "ignores earlier versions of library" in {
    val (_, libraryVersions) = createLibraryWithMultipleVersions(org)
    val project = createProject(org)
    addLibraryVersion(project, libraryVersions.last)
    libraryRecommendationsDao.forProject(project) must be(Nil)
  }

  "with library to upgrade" in {
    val (library, libraryVersions) = createLibraryWithMultipleVersions(org)
    val project = createProject(org)
    addLibraryVersion(project, libraryVersions.head)
    eventually {
      verify(
        libraryRecommendationsDao.forProject(project),
        Seq(
          LibraryRecommendation(
            library = library,
            from = "1.0.0",
            to = libraryVersions.last,
            latest = libraryVersions.last
          )
        )
      )
    }
  }

  "suggests upgrade even if current version is missing" in {
    val (_, libraryVersions) = createLibraryWithMultipleVersions(org)
    val project = createProject(org)
    val oldest = libraryVersions.head

    addLibraryVersion(project, oldest)
    libraryVersionsDao.delete(Constants.SystemUser, oldest)
    eventually {
      libraryRecommendationsDao.forProject(project) must not be (Nil)
    }
  }

  "Prefers latest production release even when more recent beta release is available" in {
    val (library, libraryVersions) = createLibraryWithMultipleVersions(org)(
      versions = Seq("1.0.0", "1.0.2-RC1", "1.0.1")
    )
    val project = createProject(org)
    addLibraryVersion(project, libraryVersions.head)
    eventually {
      verify(
        libraryRecommendationsDao.forProject(project),
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

  // TODO: Add tests specific to cross build versions

}
