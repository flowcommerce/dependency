package lib

import db.{Authorization, InternalProjectLibrary, ProjectsDao}
import io.flow.dependency.v0.models.{Project, ProjectDetail, ProjectLibrary, Reference}
import javax.inject.Inject

class Conversions @Inject() (
  projectsDao: ProjectsDao
) {

  def toProjectLibraryModels(libraries: Seq[InternalProjectLibrary]): Seq[ProjectLibrary] = {
    val projectsById = projectsDao
      .findAll(
        Authorization.All,
        ids = Some(libraries.map(_.projectId).distinct),
        limit = None
      )
      .map { p => p.id -> p }
      .toMap

    libraries.flatMap { pl =>
      projectsById.get(pl.projectId).map { project =>
        ProjectLibrary(
          id = pl.id,
          project = toProjectDetailModel(project),
          groupId = pl.groupId,
          artifactId = pl.artifactId,
          version = pl.db.version,
          crossBuildVersion = pl.db.crossBuildVersion,
          path = pl.db.path,
          library = pl.db.libraryId.map { id => Reference(id) }
        )
      }
    }
  }

  def toProjectLibraryModel(pl: InternalProjectLibrary): Option[ProjectLibrary] = {
    toProjectLibraryModels(Seq(pl)).headOption
  }

  private[this] def toProjectDetailModel(project: Project): ProjectDetail = {
    ProjectDetail(
      id = project.id,
      organization = project.organization,
      name = project.name
    )
  }
}
