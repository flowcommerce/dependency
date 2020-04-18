package dependency.resolver

import org.scalatest.{MustMatchers, WordSpec}

class DependencyResolverSpec extends WordSpec with MustMatchers with ResolverHelpers {

  private[this] val dependencyResolver: DependencyResolver = DependencyResolver()
  private[this] val libS3 = makeProjectInfo(
    projectId = "lib-s3",
    provides = Seq("io.flow.lib-s3"),
  )
  private[this] val libInvoice = makeProjectInfo(
    projectId = "lib-invoice",
    provides = Seq("io.flow.lib-invoice-generator"),
    dependsOn = Seq("io.flow.lib-s3"),
  )
  private[this] val ftp = makeProjectInfo(
    projectId = "ftp",
    dependsOn = Seq("io.flow.lib-s3"),
  )
  private[this] val billing = makeProjectInfo(
    projectId = "billing",
    dependsOn = Seq("io.flow.lib-invoice-generator"),
  )

  // simplify output for failing test cases
  private[this] case class DependencyResolutionIds(
    resolved: List[Seq[String]],
    circular: Seq[String],
  )
  private[this] val EmptyDependencyResolutionIds = DependencyResolutionIds(resolved = Nil, circular = Nil)

  private[this] def resolve(projects: Seq[ProjectInfo]): DependencyResolutionIds = {
    val r = dependencyResolver.resolve(projects)
    DependencyResolutionIds(
      resolved = r.resolved.map(_.map(_.projectId).sorted),
      circular = r.circular.map(_.projectId).sorted,
    )
  }

  "no projects" in {
    dependencyResolver.resolve(Nil) must equal(DependencyResolution.empty)
  }

  "projects w/ no dependencies" in {
    val projects = Seq(makeProjectInfo(), makeProjectInfo())
    resolve(projects) must equal(
      EmptyDependencyResolutionIds.copy(
        resolved = List(projects.map(_.projectId).sorted)
      )
    )
  }

  "projects w/ resolvable dependencies" in {
    resolve(
      Seq(billing, ftp, libS3, libInvoice)
    ) must equal(
      EmptyDependencyResolutionIds.copy(
        resolved = List(
          Seq(libS3.projectId),
          Seq(ftp.projectId, libInvoice.projectId).sorted,
          Seq(billing.projectId),
        )
      )
    )
  }

  "projects w/ circular dependencies" in {
    val libA = makeProjectInfo(
      projectId = "lib-a",
      dependsOn = Seq("io.flow.lib-b"),
      provides = Seq("io.flow.lib-a"),
    )
    val libB = makeProjectInfo(
      projectId = "lib-b",
      dependsOn = Seq("io.flow.lib-c"),
      provides = Seq("io.flow.lib-b"),
    )
    val libC = makeProjectInfo(
      projectId = "lib-c",
      dependsOn = Seq("io.flow.lib-a"),
      provides = Seq("io.flow.lib-c"),
    )
    val projects = Seq(libA, libB, libC)

    resolve(projects) must equal(
      EmptyDependencyResolutionIds.copy(
        circular = projects.map(_.projectId).sorted
      )
    )
  }
}
