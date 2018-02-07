package db

import javax.inject.{Inject, Singleton}

import io.flow.dependency.api.lib.Recommendations
import io.flow.dependency.v0.models.{Binary, BinaryVersion, Project, ProjectBinary, VersionForm}
import io.flow.postgresql.Pager
import anorm._
import com.google.inject.Provider
import play.api.db._
import play.api.Play.current
import play.api.libs.json._

case class BinaryRecommendation(
  binary: Binary,
  from: String,
  to: BinaryVersion,
  latest: BinaryVersion
)

@Singleton
class BinaryRecommendationsDao @Inject()(
  db: Database,
  binaryVersionsDaoProvider: Provider[BinaryVersionsDao],
  projectBinariesDaoProvider: Provider[ProjectBinariesDao],
  binariesDaoProvider: Provider[BinariesDao]
) {

  def forProject(project: Project): Seq[BinaryRecommendation] = {
    var recommendations = scala.collection.mutable.ListBuffer[BinaryRecommendation]()
    val auth = Authorization.Organization(project.organization.id)

    Pager.create { offset =>
      projectBinariesDaoProvider.get.findAll(
        Authorization.Organization(project.organization.id),
        projectId = Some(project.id),
        hasBinary = Some(true),
        offset = offset
      )
    }.foreach { projectBinary =>
      projectBinary.binary.flatMap { lib => binariesDaoProvider.get.findById(auth, lib.id) }.map { binary =>
        val recentVersions = versionsGreaterThan(auth, binary, projectBinary.version)
        recommend(projectBinary, recentVersions).map { v =>
          recommendations ++= Seq(
            BinaryRecommendation(
              binary = binary,
              from = projectBinary.version,
              to = v,
              latest = recentVersions.headOption.getOrElse(v)
            )
          )
        }
      }
    }

    recommendations
  }

  def recommend(current: ProjectBinary, others: Seq[BinaryVersion]): Option[BinaryVersion] = {
    Recommendations.version(
      VersionForm(current.version),
      others.map(v => VersionForm(v.version))
    ).map { version =>
      others.find {
        _.version == version
      }.getOrElse {
        sys.error(s"Failed to find recommended binary with version[$version]")
      }
    }
  }

  /**
    * Returns all versions of a binary greater than the one specified
    */
  private[this] def versionsGreaterThan(auth: Authorization, binary: Binary, version: String): Seq[BinaryVersion] = {
    var recommendations = scala.collection.mutable.ListBuffer[BinaryVersion]()
    Pager.create { offset =>
      binaryVersionsDaoProvider.get.findAll(
        auth,
        binaryId = Some(binary.id),
        greaterThanVersion = Some(version),
        offset = offset
      )
    }.foreach { binaryVersion =>
      recommendations ++= Seq(binaryVersion)
    }
    recommendations
  }

}
