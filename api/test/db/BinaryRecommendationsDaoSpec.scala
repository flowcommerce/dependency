package db

import io.flow.dependency.v0.models.{Binary, BinaryVersion, Organization, Project}
import util.DependencySpec

class BinaryRecommendationsDaoSpec extends DependencySpec {

  def createBinaryWithMultipleVersions(
    org: Organization
  ) (
    implicit versions: Seq[String] = Seq("1.0.0", "1.0.1", "1.0.2")
  ): (Binary, Seq[BinaryVersion]) = {
    val binary = createBinary(org)(createBinaryForm(org))
    versions.map { version =>
      createBinaryVersion(org)(
        binary = binary,
        version = version
      )
    }
    (
      binary,
      binaryVersionsDao.findAll(
        Authorization.Organization(org.id),
        binaryId = Some(binary.id),
        limit = versions.size
      ).reverse
    )
  }

  def addBinaryVersion(project: Project, binaryVersion: BinaryVersion) {
    val projectBinary = create(
      projectBinariesDao.upsert(
        systemUser,
        ProjectBinaryForm(
          projectId = project.id,
          name = binaryVersion.binary.name,
          version = binaryVersion.version,
          path = "test.sbt"
        )
      )
    )
    projectBinariesDao.setBinary(systemUser, projectBinary, binaryVersion.binary)
  }

  lazy val org = createOrganization()

  "no-op if nothing to upgrade" in {
    val project = createProject(org)
    binaryRecommendationsDao.forProject(project) must be(Nil)
  }

  "ignores earlier versions of binary" in {
    val (binary, binaryVersions) = createBinaryWithMultipleVersions(org)
    val project = createProject(org)
    addBinaryVersion(project, binaryVersions.last)
    binaryRecommendationsDao.forProject(project) must be(Nil)
  }

  "with binary to upgrade" in {
    val (binary, binaryVersions) = createBinaryWithMultipleVersions(org)
    val project = createProject(org)
    addBinaryVersion(project, binaryVersions.find(_.version == "1.0.0").get)
    verify(
      binaryRecommendationsDao.forProject(project),
      Seq(
        BinaryRecommendation(
          binary = binary,
          from = "1.0.0",
          to = binaryVersions.find(_.version == "1.0.2").get,
          latest = binaryVersions.find(_.version == "1.0.2").get
        )
      )
    )
  }

  "Prefers latest production release even when more recent beta release is available" in {
    val (binary, binaryVersions) = createBinaryWithMultipleVersions(org)(
      versions = Seq("1.0.0", "1.0.2-RC1", "1.0.1")
    )
    val project = createProject(org)
    addBinaryVersion(project, binaryVersions.find(_.version == "1.0.0").get)
    verify(
      binaryRecommendationsDao.forProject(project),
      Seq(
        BinaryRecommendation(
          binary = binary,
          from = "1.0.0",
          to = binaryVersions.find(_.version == "1.0.1").get,
          latest = binaryVersions.find(_.version == "1.0.2-RC1").get
        )
      )
    )
  }


  def verify(actual: Seq[BinaryRecommendation], expected: Seq[BinaryRecommendation]) {
    (actual == expected) match {
      case true => {}
      case false => {
        (actual.size == expected.size) match {
          case false => {
            sys.error(s"Expected[${expected.size}] recommendations but got [${actual.size}]")
          }
          case true => {
            (actual zip expected).map { case (a, b) =>
              (a == b) match {
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

}
