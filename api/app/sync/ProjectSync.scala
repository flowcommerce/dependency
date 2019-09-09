package sync

import db._
import io.flow.common.v0.models.UserReference
import io.flow.dependency.actors.SearchActor
import io.flow.dependency.v0.models.Project
import io.flow.postgresql.Pager
import javax.inject.Inject

class ProjectSync @Inject()(
  projectsDao: ProjectsDao,
  syncsDao: SyncsDao,
  @javax.inject.Named("search-actor") searchActor: akka.actor.ActorRef,
) {

  def sync(user: UserReference, projectId: String): Unit = {
    println(s"project sync starting for user:$user")
    projectsDao.findById(Authorization.All, projectId).foreach { project =>
      syncsDao.withStartedAndCompleted("project", project.id) {
        searchActor ! SearchActor.Messages.SyncProject(project.id)
      }
    }
  }

  def forall(f: Project => Any): Unit = {
    Pager.create { offset =>
      projectsDao.findAll(Authorization.All, offset = offset, limit = 1000)
    }.foreach { rec =>
      f(rec)
    }
  }
}