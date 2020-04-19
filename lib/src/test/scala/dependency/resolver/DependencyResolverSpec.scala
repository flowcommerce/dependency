package dependency.resolver

import org.scalatest.{Assertion, MustMatchers, WordSpec}

class DependencyResolverSpec extends WordSpec with MustMatchers with ResolverHelpers {

  private[this] def makeFlowLibRef(artifactId: String): LibraryReference = makeLibraryReference(
    groupId = "io.flow",
    artifactId = artifactId,
  )

  private[this] val libS3 = makeProjectInfo(
    projectId = "lib-s3",
    provides = Seq(makeFlowLibRef("lib-s3")),
  )
  private[this] val libInvoice = makeProjectInfo(
    projectId = "lib-invoice",
    provides = Seq(makeFlowLibRef("lib-invoice-generator")),
    dependsOn = Seq(makeFlowLibRef("lib-s3")),
  )
  private[this] val ftp = makeProjectInfo(
    projectId = "ftp",
    dependsOn = Seq(makeFlowLibRef("lib-s3")),
  )
  private[this] val billing = makeProjectInfo(
    projectId = "billing",
    dependsOn = Seq(makeFlowLibRef("lib-invoice-generator")),
  )

  // simplify output for failing test cases
  private[this] case class DependencyResolutionIds(
    resolved: List[Seq[String]],
    circular: Seq[String],
  )
  private[this] val EmptyDependencyResolutionIds = DependencyResolutionIds(resolved = Nil, circular = Nil)

  private[this] def resolve(projects: Seq[ProjectInfo]): DependencyResolutionIds = {
    val r = DependencyResolver(projects).resolution
    DependencyResolutionIds(
      resolved = r.resolved.map(_.map(_.projectId).sorted),
      circular = r.unresolved.map(_.projectId).sorted,
    )
  }

  "no projects" in {
    DependencyResolver(Nil).resolution must equal(DependencyResolution.empty)
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
      dependsOn = Seq(makeFlowLibRef("lib-b"), makeFlowLibRef("lib-s3"), makeFlowLibRef("other")),
      provides = Seq(makeFlowLibRef("lib-a")),
    )
    val libB = makeProjectInfo(
      projectId = "lib-b",
      dependsOn = Seq(makeFlowLibRef("lib-c")),
      provides = Seq(makeFlowLibRef("lib-b")),
    )
    val libC = makeProjectInfo(
      projectId = "lib-c",
      dependsOn = Seq(makeFlowLibRef("lib-a")),
      provides = Seq(makeFlowLibRef("lib-c")),
    )

    val r = DependencyResolver(Seq(libA, libB, libC, libS3)).resolution
    r.unresolved.size must equal(3)
    def find(p: ProjectInfo)(f: ProjectInfo => Assertion) = {
      f(r.unresolved.find(_.projectId == p.projectId).get)
    }

    find(libA) { i =>
      i.resolvedDependencies.map(_.identifier) must equal(Seq("io.flow.lib-s3"))
      i.unresolvedDependencies.map(_.identifier) must equal(Seq("io.flow.lib-b"))
      i.unknownLibraries.map(_.identifier) must equal(Seq("io.flow.other"))
    }
    find(libB) { i =>
      i.resolvedDependencies must be(Nil)
      i.unresolvedDependencies.map(_.identifier) must equal(Seq("io.flow.lib-c"))
      i.unknownLibraries.map(_.identifier) must equal(Nil)
    }
    find(libC) { i =>
      i.resolvedDependencies must be(Nil)
      i.unresolvedDependencies.map(_.identifier) must equal(Seq("io.flow.lib-a"))
      i.unknownLibraries.map(_.identifier) must equal(Nil)
    }
  }
}
