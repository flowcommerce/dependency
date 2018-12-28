package db

import javax.inject.{Inject, Singleton}

import io.flow.dependency.api.lib.Recommendations
import io.flow.dependency.v0.models.{Library, LibraryVersion, Project, ProjectLibrary, VersionForm}
import com.google.inject.Provider

case class LibraryRecommendation(
  library: Library,
  from: String,
  to: LibraryVersion,
  latest: LibraryVersion
)

@Singleton
class LibraryRecommendationsDao @Inject()(
  libraryVersionsDaoProvider: Provider[LibraryVersionsDao],
  projectLibrariesDaoProvider: Provider[ProjectLibrariesDao],
  librariesDaoProvider: Provider[LibrariesDao]
){

  def forProject(project: Project): Seq[LibraryRecommendation] = {
    val recommendations = scala.collection.mutable.ListBuffer[LibraryRecommendation]()
    val auth = Authorization.Organization(project.organization.id)

    projectLibrariesDaoProvider.get.findAll(
      Authorization.Organization(project.organization.id),
      projectId = Some(project.id),
      hasLibrary = Some(true),
      limit = None
    ).foreach { projectLibrary =>
      projectLibrary.library.flatMap { lib => librariesDaoProvider.get.findById(auth, lib.id) }.map { library =>
        val recentVersions = versionsGreaterThan(auth, library, projectLibrary.version)
        recommend(projectLibrary, recentVersions).map { v =>
          recommendations ++= Seq(
            LibraryRecommendation(
              library = library,
              from = projectLibrary.version,
              to = v,
              latest = recentVersions.headOption.getOrElse(v)
            )
          )
        }
      }
    }

    recommendations
  }

  def recommend(current: ProjectLibrary, others: Seq[LibraryVersion]): Option[LibraryVersion] = {
    Recommendations.version(
      VersionForm(current.version, current.crossBuildVersion),
      others.map(v => VersionForm(v.version, v.crossBuildVersion))
    ).map { version =>
      others.find { _.version == version }.getOrElse {
        sys.error(s"Failed to find recommended library with version[$version]")
      }
    }
  }

  /**
   * Returns all versions of a library greater than the one specified
   */
  private[this] def versionsGreaterThan(
    auth: Authorization,
    library: Library,
    version: String
  ): Seq[LibraryVersion] = {
    libraryVersionsDaoProvider.get.findAll(
      auth,
      libraryId = Some(library.id),
      greaterThanVersion = Some(version),
      limit = None
    )
  }

}
