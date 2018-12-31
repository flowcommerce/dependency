package io.flow.dependency.www.lib

import io.flow.common.v0.models.User
import io.flow.util.{Config => FlowConfig}
import io.flow.dependency.lib.Urls

sealed trait Section

object Section {
  case object Dashboard extends Section
  case object Projects extends Section
  case object Binaries extends Section
  case object Libraries extends Section
  case object Members extends Section
  case object Resolvers extends Section
  case object Subscriptions extends Section
}

case class UiData(
  requestPath: String,
  organization: Option[String] = None,
  section: Option[Section] = None,
  title: Option[String] = None,
  headTitle: Option[String] = None,
  user: Option[User] = None,
  query: Option[String] = None,
  config: FlowConfig
) {

  lazy val urls = Urls(config)

}
