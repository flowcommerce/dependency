package lib

import io.flow.dependency.v0.models.Project

object Hacks {
  def isMetric(project: Project): Boolean = project.name == "metric"

  val flowGroupId: String = "io.flow"
  val flowOrgKey: String = "flow"
}
