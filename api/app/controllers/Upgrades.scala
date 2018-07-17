package controllers

import javax.inject.{Inject, Singleton}

import db.{Authorization, ProjectsDao}
import io.flow.play.controllers.InjectedFlowController
import lib.UpgradeService

@Singleton
class Upgrades @Inject()(upgradeService: UpgradeService,
                         projectsDao: ProjectsDao)
    extends InjectedFlowController {

  def postLibraryById(id: String) = Anonymous {
    projectsDao.findById(Authorization.All, id) match {
      case Some(library) =>
        upgradeService.upgradeLibrary(library)
        Ok(s"Upgraded library ${library.name}")

      case _ =>
        NotFound
    }
  }
}
