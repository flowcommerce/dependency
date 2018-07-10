package util

import java.util.UUID

import db._
import io.flow.common.v0.models.{Name, User, UserReference}
import io.flow.dependency.v0.models._
import io.flow.play.util.{Config, Random}
import io.flow.test.utils.FlowPlaySpec
import org.scalatest.concurrent.Eventually._
import org.scalatest.time.{Seconds, Span}
import play.api.db.Database

trait DependencySpec extends FlowPlaySpec with Factories {

  implicit val organizationsDao = init[OrganizationsDao]
  implicit val binariesDao = init[BinariesDao]
  implicit val binaryVersionsDao = init[BinaryVersionsDao]
  implicit val librariesDao = init[LibrariesDao]
  implicit val libraryVersionsDao = init[LibraryVersionsDao]
  implicit val usersDao = init[UsersDao]
  implicit val projectsDao = init[ProjectsDao]
  implicit val projectLibrariesDao = init[ProjectLibrariesDao]
  implicit val projectBinariesDao = init[ProjectBinariesDao]
  implicit val githubUsersDao = init[GithubUsersDao]
  implicit val tokensDao = init[TokensDao]
  implicit val syncsDao = init[SyncsDao]
  implicit val resolversDao = init[ResolversDao]
  implicit val membershipsDao = init[MembershipsDao]
  implicit val itemsDao = init[ItemsDao]
  implicit val subscriptionsDao = init[SubscriptionsDao]
  implicit val lastEmailsDao = init[LastEmailsDao]
  implicit val binaryRecommendationsDao = init[BinaryRecommendationsDao]
  implicit val libraryRecommendationsDao = init[LibraryRecommendationsDao]
  implicit val recommendationsDao = init[RecommendationsDao]
  implicit val userIdentifiersDao = init[UserIdentifiersDao]
  implicit val config = init[Config]
  implicit val db = init[Database]

  val random = Random()

  import scala.language.implicitConversions
  implicit def toUserReference(user: User): UserReference = UserReference(id = user.id)

  lazy val systemUser = createUser()

  def create[T](result: Either[Seq[String], T]): T = {
    result match {
      case Left(errors) => sys.error(errors.mkString(", "))
      case Right(obj) => obj
    }
  }

  def eventuallyInNSeconds[T](n: Int)(f: => T): T = {
    eventually(timeout(Span(n, Seconds))) {
      f
    }
  }

  /**
    * Function called on each iteration until it returns true, up
    * until maxAttempts (at which point an error is raised)
    */
  def waitFor(
    function: () => Boolean,
    maxAttempts: Int = 25,
    msBetweenAttempts: Int = 250
  ): Boolean = {
    var ctr = 0
    var found = false
    while (!found) {
      found = function()
      ctr += 1
      if (ctr > maxAttempts) {
        sys.error("Did not create user organization")
      }
      Thread.sleep(msBetweenAttempts)
    }
    true
  }

  def createOrganization(
    form: OrganizationForm = createOrganizationForm(),
    user: User = systemUser
  ): Organization = {
    organizationsDao.create(user, form).right.getOrElse {
      sys.error("Failed to create organization")
    }
  }

  def createTestKey(): String = {
    s"z-test-${UUID.randomUUID.toString.toLowerCase}"
  }

  def createOrganizationForm() = {
    OrganizationForm(
      key = createTestKey()
    )
  }

  def createBinary(
    org: Organization = createOrganization()
  ) (
    implicit form: BinaryForm = createBinaryForm(org)
  ): Binary = {
    binariesDao.create(systemUser, form).right.getOrElse {
      sys.error("Failed to create binary")
    }
  }

  def createBinaryForm(
    org: Organization = createOrganization()
  ) = BinaryForm(
    organizationId = org.id,
    name = BinaryType.UNDEFINED(s"z-test-binary-${UUID.randomUUID.toString}".toLowerCase)
  )

  def createBinaryVersion(
    org: Organization = createOrganization()
  ) (
    implicit binary: Binary = createBinary(org),
             version: String = s"0.0.1-${UUID.randomUUID.toString}".toLowerCase
  ): BinaryVersion = {
    binaryVersionsDao.create(systemUser, binary.id, version)
  }

  def createLibrary(
    org: Organization = createOrganization(),
    user: User = systemUser
  ) (
    implicit form: LibraryForm = createLibraryForm(org, user)
  ): Library = {
    librariesDao.create(user, form).right.getOrElse {
      sys.error("Failed to create library")
    }
  }

  def createLibraryForm(
    org: Organization = createOrganization(),
    user: User = systemUser
  ) (
    implicit versionForm: VersionForm = VersionForm("0.0.1"),
             resolver: Resolver = createResolver(org, user)
  ) = LibraryForm(
    organizationId = org.id,
    groupId = s"z-test.${UUID.randomUUID.toString}".toLowerCase,
    artifactId = s"z-test-${UUID.randomUUID.toString}".toLowerCase,
    version = Some(versionForm),
    resolverId = resolver.id
  )

  def createLibraryVersion(
    org: Organization = createOrganization(),
    user: User = systemUser
  ) (
    implicit library: Library = createLibrary(org, user),
             version: VersionForm = createVersionForm()
  ): LibraryVersion = {
    libraryVersionsDao.create(user, library.id, version)
  }

  def createVersionForm(
    version: String = s"0.0.1-${UUID.randomUUID.toString}".toLowerCase,
    crossBuildVersion: Option[String] = None
  ) = {
    VersionForm(version, crossBuildVersion)
  }

  def createProject(
    org: Organization = createOrganization()
  ) (
    implicit form: ProjectForm = createProjectForm(org)
  ): Project = {
    val user = organizationsDao.findByKey(Authorization.All, form.organization).flatMap { org =>
      usersDao.findById(org.user.id)
    }.getOrElse {
      sys.error("Could not find user that created org")
    }

    create(projectsDao.create(user, form))
  }

  def createProjectForm(
    org: Organization = createOrganization()
  ) = {
    ProjectForm(
      organization = org.key,
      name = createTestName(),
      visibility = Visibility.Private,
      scms = Scms.Github,
      uri = s"http://github.com/test/${UUID.randomUUID.toString}"
    )
  }

  def createProjectWithLibrary(
    org: Organization = createOrganization(),
    version: VersionForm = VersionForm(version = "0.0.1")
  ) (
    implicit libraryForm: LibraryForm = createLibraryForm(org).copy(
      groupId = s"z-test-${UUID.randomUUID.toString}".toLowerCase,
      artifactId = s"z-test-${UUID.randomUUID.toString}".toLowerCase
    )
  ): (Project, LibraryVersion) = {
    val project = createProject(org)
    val library = createLibrary(org)(libraryForm)

    val projectLibrary = createProjectLibrary(project)(
      createProjectLibraryForm(
        project,
        groupId = library.groupId,
        artifactId = library.artifactId,
        version = version.version,
        crossBuildVersion = version.crossBuildVersion
      )
    )

    val libraryVersion = libraryVersionsDao.upsert(systemUser, library.id, version)

    projectLibrariesDao.setLibrary(systemUser, projectLibrary, library)

    (project, libraryVersion)
  }

  def createProjectWithBinary(
    org: Organization = createOrganization()
  ): (Project, BinaryVersion) = {
    val binary = createBinary(org)
    val binaryVersion = createBinaryVersion(org)(binary = binary)
    val project = createProject(org)

    val projectBinary = create(projectBinariesDao.create(
      systemUser,
      createProjectBinaryForm(
        project = project,
        name = binary.name,
        version = binaryVersion.version
      )
    ))

    projectBinariesDao.setBinary(systemUser, projectBinary, binary)

    (project, binaryVersion)
  }

  def makeUser(
    form: UserForm = makeUserForm()
  ): User = {
    User(
      id = io.flow.play.util.IdGenerator("tst").randomId(),
      email = form.email,
      name = form.name.getOrElse(Name())
    )
  }

  def makeUserForm() = UserForm(
    email = None,
    name = None
  )

  def createUser(
    form: UserForm = createUserForm()
  ): User = {
    create(usersDao.create(None, form))
  }

  def createUserForm(
    email: String = createTestEmail(),
    name: Option[Name] = None
  ) = UserForm(
    email = Some(email),
    name = name
  )

  def createGithubUser(
    form: GithubUserForm = createGithubUserForm()
  ): GithubUser = {
    githubUsersDao.create(None, form)
  }

  def createGithubUserForm(
    user: User = createUser(),
    githubUserId: Long = random.positiveLong(),
    login: String = createTestKey()
  ) = {
    GithubUserForm(
      userId = user.id,
      githubUserId = githubUserId,
      login = login
    )
  }

  def createToken(
    form: TokenForm = createTokenForm()
  ): Token = {
    create(tokensDao.create(systemUser, InternalTokenForm.UserCreated(form)))
  }

  def createTokenForm(
    user: User = createUser()
  ):TokenForm = {
    TokenForm(
      userId = user.id,
      description = None
    )
  }

  def createSync(
    form: SyncForm = createSyncForm()
  ): Sync = {
    syncsDao.create(systemUser, form)
  }

  def createSyncForm(
    `type`: String = "test",
    objectId: String = UUID.randomUUID.toString,
    event: SyncEvent = SyncEvent.Started
  ) = {
    SyncForm(
      `type` = `type`,
      objectId = objectId,
      event = event
    )
  }

  def createResolver(
    org: Organization,
    user: User = systemUser
  ) (
    implicit form: ResolverForm = createResolverForm(org)
  ): Resolver = {
    create(resolversDao.create(user, form))
  }

  def createResolverForm(
    org: Organization = createOrganization(),
    visibility: Visibility = Visibility.Private,
    uri: String = s"http://${UUID.randomUUID.toString}.z-test.flow.io"
  ) = {
    ResolverForm(
      visibility = visibility,
      organization = org.key,
      uri = uri
    )
  }

  def createMembership(
    form: MembershipForm = createMembershipForm()
  ): Membership = {
    create(membershipsDao.create(systemUser, form))
  }

  def createMembershipForm(
    org: Organization = createOrganization(),
    user: User = createUser(),
    role: Role = Role.Member
  ) = {
    MembershipForm(
      organization = org.key,
      userId = user.id,
      role = role
    )
  }

  def createLibraryWithMultipleVersions(
    org: Organization,
    versions: Seq[String] = Seq("1.0.0", "1.0.1", "1.0.2")
  ): (Library, Seq[LibraryVersion]) = {
    val library = createLibrary(org)(createLibraryForm(org).copy(version = None))
    (
      library,
      versions.map { version =>
        createLibraryVersion(
          org
        ) (
          library = library,
          version = VersionForm(version = version)
        )
      }
    )
  }

  def addLibraryVersion(project: Project, libraryVersion: LibraryVersion) {
    val projectLibrary = create(
      projectLibrariesDao.upsert(
        systemUser,
        ProjectLibraryForm(
          projectId = project.id,
          groupId = libraryVersion.library.groupId,
          artifactId = libraryVersion.library.artifactId,
          path = "test.sbt",
          version = VersionForm(libraryVersion.version, libraryVersion.crossBuildVersion)
        )
      )
    )

    projectLibrariesDao.setLibrary(systemUser, projectLibrary, libraryVersion.library)
  }

  def replaceItem(
    org: Organization
  ) (
    implicit form: ItemForm = createItemForm(org)
  ): Item = {
    itemsDao.replace(systemUser, form)
  }

  def createItemSummary(
    org: Organization
  ) (
    implicit binary: Binary = createBinary(org)
  ): ItemSummary = {
    BinarySummary(
      id = binary.id,
      organization = OrganizationSummary(org.id, org.key),
      name = binary.name
    )
  }

  def createItemForm(
    org: Organization
  ) (
    implicit summary: ItemSummary = createItemSummary(org)
  ): ItemForm = {
    val label = summary match {
      case BinarySummary(id, org, name) => name.toString
      case LibrarySummary(id, org, groupId, artifactId) => Seq(groupId, artifactId).mkString(".")
      case ProjectSummary(id, org, name) => name
      case ItemSummaryUndefinedType(name) => name
    }
    ItemForm(
      summary = summary,
      label = label,
      description = None,
      contents = label
    )
  }

  def createSubscription(
    form: SubscriptionForm = createSubscriptionForm(),
    user: User = systemUser
  ): Subscription = {
    subscriptionsDao.upsertByUserIdAndPublication(user, form)
    subscriptionsDao.findByUserIdAndPublication(form.userId, form.publication).get
  }

  def createSubscriptionForm(
    user: User = createUser(),
    publication: Publication = Publication.DailySummary
  ) = {
    SubscriptionForm(
      userId = user.id,
      publication = publication
    )
  }

  def createLastEmail(
    form: LastEmailForm = createLastEmailForm()
  ): LastEmail = {
    lastEmailsDao.record(systemUser, form)
  }

  def createLastEmailForm(
    user: User = createUser(),
    publication: Publication = Publication.DailySummary
  ) = LastEmailForm(
    userId = user.id,
    publication = publication
  )

  def createProjectLibrary(
    project: Project = createProject()
  ) (
    implicit form: ProjectLibraryForm = createProjectLibraryForm(project)
  ): ProjectLibrary = {
    create(projectLibrariesDao.create(systemUser, form))
  }

  def createProjectLibraryForm(
    project: Project = createProject(),
    groupId: String = s"z-test.${UUID.randomUUID.toString}".toLowerCase,
    artifactId: String = s"z-test-${UUID.randomUUID.toString}".toLowerCase,
    path: String = "build.sbt",
    version: String = "0.0.1",
    crossBuildVersion: Option[String] = None
  ) = {
    ProjectLibraryForm(
      projectId = project.id,
      groupId = groupId,
      artifactId = artifactId,
      path = path,
      version = VersionForm(version, crossBuildVersion)
    )
  }

  def createProjectBinary(
    project: Project = createProject()
  ) (
    implicit form: ProjectBinaryForm = createProjectBinaryForm(project)
  ): ProjectBinary = {
    create(projectBinariesDao.create(systemUser, form))
  }

  def createProjectBinaryForm(
    project: Project = createProject(),
    name: BinaryType = BinaryType.UNDEFINED(createTestKey()),
    path: String = "build.sbt",
    version: String = "0.0.1"
  ) = {
    ProjectBinaryForm(
      projectId = project.id,
      name = name,
      path = path,
      version = version
    )
  }

}
