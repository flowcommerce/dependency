package controllers

import javax.inject.{Inject, Singleton}

import io.flow.play.controllers.InjectedFlowController
import lib.UpgradeService
import play.api.mvc.{Action, AnyContent}

@Singleton
class Upgrades @Inject()(upgradeService: UpgradeService)
    extends InjectedFlowController {

  def postLibraryById(name: String): Action[AnyContent] = Anonymous.async {
    upgradeService
      .upgradeLibrary(name).map {
        _.map { library =>
          Ok(s"Upgraded library [$library]")
        }
        .getOrElse(NotFound(s"Library not recognized: [$name]"))
    }.unsafeToFuture()
  }
}
