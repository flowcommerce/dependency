package db

import javax.inject.{Inject, Singleton}

import io.flow.dependency.api.lib.Recommendations
import io.flow.dependency.v0.models.{Library, LibraryVersion, Project, VersionForm}
import com.google.inject.Provider

case class LibraryRecommendation(
  library: Library,
  from: String,
  to: LibraryVersion,
  latest: LibraryVersion
)

@Singleton
class LibraryRecommendationsDao @Inject()(
  internalProjectLibrariesDao: InternalProjectLibrariesDao,
  libraryVersionsDaoProvider: Provider[LibraryVersionsDao],
  librariesDaoProvider: Provider[LibrariesDao],
){

  def forProject(project: Project): Seq[LibraryRecommendation] = {
    val recommendations = scala.collection.mutable.ListBuffer[LibraryRecommendation]()
    val auth = Authorization.Organization(project.organization.id)

    internalProjectLibrariesDao.findAll(
      Authorization.Organization(project.organization.id),
      projectId = Some(project.id),
      hasLibrary = Some(true),
      limit = None,
      orderBy = None,
    ).foreach { projectLibrary =>
      projectLibrary.db.libraryId.flatMap { libraryId => librariesDaoProvider.get.findById(auth, libraryId) }.map { library =>
        val recentVersions = versionsGreaterThan(auth, library, projectLibrary)
        recommend(projectLibrary, recentVersions).map { v =>
          recommendations ++= Seq(
            LibraryRecommendation(
              library = library,
              from = projectLibrary.db.version,
              to = v,
              latest = recentVersions.headOption.getOrElse(v)
            )
          )
        }
      }
    }

    recommendations.toSeq
  }

  def recommend(current: InternalProjectLibrary, others: Seq[LibraryVersion]): Option[LibraryVersion] = {
    Recommendations.version(
      VersionForm(current.db.version, current.db.crossBuildVersion),
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
    projectLibrary: InternalProjectLibrary,
  ): Seq[LibraryVersion] = {
    libraryVersionsDaoProvider.get.findAll(
      auth,
      libraryId = Some(library.id),
      // we don't use greaterThanVersion because it assumes the current version is present in the DB
      greaterThanSortKey = Some(projectLibrary.sortKey),
      limit = None
    )
  }

}
