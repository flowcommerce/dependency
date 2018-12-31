package db

import util.DependencySpec

class LibraryRecommendationsDaoSpec extends  DependencySpec {

  lazy val org = createOrganization()

  def verify(actual: Seq[LibraryRecommendation], expected: Seq[LibraryRecommendation]): Unit = {
    actual == expected match {
      case true => {}
      case false => {
        actual.size == expected.size match {
          case false => {
            sys.error(s"Expected[${expected.size}] recommendations but got [${actual.size}]")
          }
          case true => {
            (actual zip expected).foreach { case (a, b) =>
              a == b match {
                case true => {}
                case false => {
                  sys.error(s"Expected[${b.from} => ${b.to.version}] but got[${a.from} => ${a.to.version}]. For latest version, expected[${b.latest.version}] but got[${a.latest.version}]")
                }
              }
            }
          }
        }
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
    verify(
      libraryRecommendationsDao.forProject(project),
      Seq(
        LibraryRecommendation(
          library = library,
          from =  "1.0.0",
          to = libraryVersions.last,
          latest = libraryVersions.last
        )
      )
    )
  }

  "Prefers latest production release even when more recent beta release is available" in {
    val (library, libraryVersions) = createLibraryWithMultipleVersions(org)(
      versions = Seq("1.0.0", "1.0.2-RC1", "1.0.1")
    )
    val project = createProject(org)
    addLibraryVersion(project, libraryVersions.head)
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

  // TODO: Add tests specific to cross build versions

}
