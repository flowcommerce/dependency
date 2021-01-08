package util

import java.util.UUID

import db._
import db.generated.ProjectLibraryForm
import io.flow.common.v0.models.{Name, User, UserReference}
import io.flow.dependency.api.lib.DefaultBinaryVersionProvider
import io.flow.dependency.v0.models._
import io.flow.test.utils.FlowPlaySpec
import io.flow.util.{Config, IdGenerator, Random}
import play.api.db.Database

trait DependencySpec extends FlowPlaySpec with Factories {

  implicit val defaultBinaryVersionProvider = init[DefaultBinaryVersionProvider]
  implicit val organizationsDao = init[OrganizationsDao]
  implicit val binariesDao = init[BinariesDao]
  implicit val binaryVersionsDao = init[BinaryVersionsDao]
  implicit val librariesDao = init[LibrariesDao]
  implicit val libraryVersionsDao = init[LibraryVersionsDao]
  implicit val usersDao = init[UsersDao]
  implicit val projectsDao = init[ProjectsDao]
  implicit val projectLibrariesDao = init[InternalProjectLibrariesDao]
  implicit val projectBinariesDao = init[ProjectBinariesDao]
  implicit val githubUsersDao = init[GithubUsersDao]
  implicit val tokensDao = init[TokensDao]
  implicit val syncsDao = init[SyncsDao]
  implicit val resolversDao = init[ResolversDao]
  implicit val membershipsDao = init[MembershipsDao]
  implicit val itemsDao = init[InternalItemsDao]
  implicit val subscriptionsDao = init[SubscriptionsDao]
  implicit val lastEmailsDao = init[LastEmailsDao]
  implicit val binaryRecommendationsDao = init[BinaryRecommendationsDao]
  implicit val libraryRecommendationsDao = init[LibraryRecommendationsDao]
  implicit val recommendationsDao = init[RecommendationsDao]
  implicit val userIdentifiersDao = init[UserIdentifiersDao]
  implicit val config = init[Config]
  implicit val db = init[Database]

  val random = Random()

  implicit def toUserReference(user: User) = UserReference(id = user.id)

  lazy val systemUser: User = createUser()

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
      Thread.sleep(msBetweenAttempts.toLong)
    }
    true
  }

  def createOrganization(
    form: OrganizationForm = createOrganizationForm(),
    user: User = systemUser
  ): Organization = {
    rightOrErrors {
      organizationsDao.create(user, form)
    }
  }

  def createTestKey(): String = {
    s"z-test-${UUID.randomUUID.toString.toLowerCase}"
  }

  def createOrganizationForm(): OrganizationForm = {
    OrganizationForm(
      key = createTestKey()
    )
  }

  def createBinary(
    org: Organization = createOrganization()
  ) (
    implicit form: BinaryForm = createBinaryForm(org)
  ): Binary = {
    rightOrErrors {
      binariesDao.create(systemUser, form)
    }
  }

  def createBinaryForm(
    org: Organization = createOrganization()
  ): BinaryForm = BinaryForm(
    organizationId = org.id,
    name = BinaryType.UNDEFINED(createTestId())
  )

  def createBinaryVersion(
    org: Organization = createOrganization()
  ) (
    implicit binary: Binary = createBinary(org),
             version: String = s"0.0.1-${UUID.randomUUID.toString}".toLowerCase
  ): BinaryVersion = {
    binaryVersionsDao.create(systemUser, binary.id, version)
  }

  def upsertLibrary(groupId: String, artifactId: String): Library = {
    librariesDao.findByGroupIdAndArtifactId(Authorization.All, groupId, artifactId).getOrElse {
      val org = createOrganization()
      createLibrary(org, systemUser)(
        createLibraryForm(org, systemUser).copy(
          groupId = groupId,
          artifactId = artifactId,
        )
      )
    }
  }

  def createLibrary(
    org: Organization = createOrganization(),
    user: User = systemUser
  ) (
    implicit form: LibraryForm = createLibraryForm(org, user)
  ): Library = {
    rightOrErrors {
      librariesDao.create(user, form)
    }
  }

  def createLibraryForm(
    org: Organization = createOrganization(),
    user: User = systemUser
  ) (
    implicit versionForm: VersionForm = VersionForm("0.0.1"),
             resolver: Resolver = createResolver(org, user)
  ): LibraryForm = LibraryForm(
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
  ): VersionForm = {
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

    rightOrErrors(projectsDao.create(user, form))
  }

  def createProjectForm(
    org: Organization = createOrganization(),
    name: String = createTestName(),
  ): ProjectForm = {
    ProjectForm(
      organization = org.key,
      name = name,
      visibility = Visibility.Private,
      scms = Scms.Github,
      uri = s"http://github.com/test/${UUID.randomUUID.toString}",
      branch = createTestId()
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

    val projectBinary = rightOrErrors(projectBinariesDao.create(
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
      id = IdGenerator("tst").randomId(),
      email = form.email,
      name = form.name.getOrElse(Name())
    )
  }

  def makeUserForm(): UserForm = UserForm(
    email = None,
    name = None
  )

  def createUser(
    form: UserForm = createUserForm()
  ): User = {
    rightOrErrors(usersDao.create(None, form))
  }

  def createUserForm(
    email: String = createTestEmail(),
    name: Option[Name] = None
  ): UserForm = UserForm(
    email = Some(email),
    name = name
  )

  def createUserIdentifier(user: User): UserIdentifier = {
    userIdentifiersDao.createForUser(testUser, user)
  }

  def createGithubUser(
    form: GithubUserForm = createGithubUserForm()
  ): GithubUser = {
    githubUsersDao.create(None, form)
  }

  def createGithubUserForm(
    user: User = createUser(),
    githubUserId: Long = random.positiveLong(),
    login: String = createTestKey()
  ): GithubUserForm = {
    GithubUserForm(
      userId = user.id,
      githubUserId = githubUserId,
      login = login
    )
  }

  def createToken(
    form: TokenForm = createTokenForm()
  ): Token = {
    rightOrErrors(tokensDao.create(systemUser, InternalTokenForm.UserCreated(form)))
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
    syncsDao.create(form)
  }

  def createSyncForm(
    `type`: String = "test",
    objectId: String = UUID.randomUUID.toString,
    event: SyncEvent = SyncEvent.Started
  ): SyncForm = {
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
    rightOrErrors(resolversDao.create(user, form))
  }

  def createResolverForm(
    org: Organization = createOrganization(),
    visibility: Visibility = Visibility.Private,
    uri: String = s"http://${UUID.randomUUID.toString}.z-test.flow.io"
  ): ResolverForm = {
    ResolverForm(
      visibility = visibility,
      organization = org.key,
      uri = uri
    )
  }

  def createMembership(
    form: MembershipForm = createMembershipForm()
  ): Membership = {
    rightOrErrors(membershipsDao.create(systemUser, form))
  }

  def createMembershipForm(
    org: Organization = createOrganization(),
    user: User = createUser(),
    role: Role = Role.Member
  ): MembershipForm = {
    MembershipForm(
      organization = org.key,
      userId = user.id,
      role = role
    )
  }

  def createLibraryWithMultipleVersions(
    org: Organization
  ) (
    implicit versions: Seq[String] = Seq("1.0.0", "1.0.1", "1.0.2")
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

  def addLibraryVersion(project: Project, libraryVersion: LibraryVersion): Unit = {
    val projectLibrary = rightOrErrors(
      projectLibrariesDao.upsert(
        systemUser,
        ProjectLibraryForm(
          organizationId = project.organization.id,
          projectId = project.id,
          groupId = libraryVersion.library.groupId,
          artifactId = libraryVersion.library.artifactId,
          path = "test.sbt",
          version = libraryVersion.version,
          crossBuildVersion = libraryVersion.crossBuildVersion,
          libraryId = None,
        )
      )
    )

    projectLibrariesDao.setLibrary(systemUser, projectLibrary, libraryVersion.library)
  }

  def replaceItem(
    org: Organization
  ) (
    implicit form: InternalItemForm = createItemForm(org)
  ): Item = {
    itemsDao.replace(systemUser, form)
  }

  def createBinarySummary(
    org: Organization
  )(
    implicit binary: Binary = createBinary(org)
  ): ItemSummary = {
    BinarySummary(
      id = binary.id,
      organization = OrganizationSummary(org.id, org.key),
      name = binary.name
    )
  }

  def createLibrarySummary(
    org: Organization
  )(
    implicit library: Library = createLibrary(org)
  ): ItemSummary = {
    LibrarySummary(
      id = library.id,
      organization = OrganizationSummary(org.id, org.key),
      groupId = library.groupId,
      artifactId = library.artifactId
    )
  }

  def createProjectSummary(
    org: Organization
  )(
    implicit project: Project = createProject(org)
  ): ProjectSummary = {
    ProjectSummary(
      id = project.id,
      organization = OrganizationSummary(org.id, org.key),
      name = project.name
    )
  }

  def createItemForm(
    org: Organization
  ) (
    implicit summary: ItemSummary = createBinarySummary(org)
  ): InternalItemForm = {
    val label = summary match {
      case BinarySummary(_, _, name) => name.toString
      case LibrarySummary(_, _, groupId, artifactId) => Seq(groupId, artifactId).mkString(".")
      case ProjectSummary(_, _, name) => name
      case ItemSummaryUndefinedType(name) => name
    }
    InternalItemForm(
      summary = summary,
      label = label,
      visibility = Visibility.Private,
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
  ): SubscriptionForm = {
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
  ): LastEmailForm = LastEmailForm(
    userId = user.id,
    publication = publication
  )

  def createProjectLibrary(
    project: Project = createProject()
  ) (
    implicit form: ProjectLibraryForm = createProjectLibraryForm(project)
  ): InternalProjectLibrary = {
    rightOrErrors(projectLibrariesDao.create(systemUser, form))
  }

  def createProjectLibraryForm(
    project: Project = createProject(),
    groupId: String = s"z-test.${UUID.randomUUID.toString}".toLowerCase,
    artifactId: String = s"z-test-${UUID.randomUUID.toString}".toLowerCase,
    path: String = "build.sbt",
    version: String = "0.0.1",
    crossBuildVersion: Option[String] = None
  ): ProjectLibraryForm = {
    ProjectLibraryForm(
      organizationId = project.organization.id,
      projectId = project.id,
      groupId = groupId,
      artifactId = artifactId,
      path = path,
      version = version,
      crossBuildVersion = crossBuildVersion,
      libraryId = None,
    )
  }

  def createProjectBinary(
    project: Project = createProject()
  ) (
    implicit form: ProjectBinaryForm = createProjectBinaryForm(project)
  ): ProjectBinary = {
    rightOrErrors(projectBinariesDao.create(systemUser, form))
  }

  def createProjectBinaryForm(
    project: Project = createProject(),
    name: BinaryType = BinaryType.UNDEFINED(createTestKey()),
    path: String = "build.sbt",
    version: String = "0.0.1"
  ): ProjectBinaryForm = {
    ProjectBinaryForm(
      projectId = project.id,
      name = name,
      path = path,
      version = version
    )
  }

}
