package controllers

import javax.inject.{Inject, Singleton}

import db.{Authorization, ProjectsDao}
import io.flow.play.controllers.InjectedFlowController
import lib.UpgradeService

@Singleton
class Upgrades @Inject()(upgradeService: UpgradeService)
    extends InjectedFlowController {

  def postLibraryById(name: String) = Anonymous {
    upgradeService
      .upgradeLibrary(name)
      .map { library =>
        Ok(s"Upgraded library [$library]")
      }
      .getOrElse(NotFound(s"Library not recognized: [$name]"))
  }
}
