package controllers

import javax.inject.{Inject, Singleton}

import db.{Authorization, ProjectsDao}
import io.flow.play.controllers.InjectedFlowController
import lib.UpgradeService

@Singleton
class Upgrades @Inject()(upgradeService: UpgradeService,
                         projectsDao: ProjectsDao)
    extends InjectedFlowController {

  def postProjectById(id: String) = Anonymous {
    projectsDao.findById(Authorization.All, id) match {
      case Some(project) =>
        upgradeService.upgradeProject(project)
        Ok(s"Upgraded project ${project.name}")

      case _ =>
        NotFound
    }
  }
}
