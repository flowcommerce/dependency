package io.flow.dependency.api.lib

import javax.inject.Inject

import db.{GithubUsersDao, InternalTokenForm, TokensDao, UsersDao}
import io.flow.dependency.v0.models.{GithubUserForm, UserForm}
import io.flow.common.v0.models.{Name, User, UserReference}
import io.flow.util.{Config, IdGenerator}
import io.flow.github.oauth.v0.{Client => GithubOauthClient}
import io.flow.github.oauth.v0.models.AccessTokenForm
import io.flow.github.v0.{Client => GithubClient}
import io.flow.github.v0.errors.UnitResponse
import io.flow.github.v0.models.{Repository => GithubRepository, User => GithubUser}

import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.ws.WSClient

case class GithubUserData(
  githubId: Long,
  login: String,
  token: String,
  emails: Seq[String],
  name: Option[String],
  avatarUrl: Option[String],
)

object GithubHelper {

  def apiClient(wsClient: WSClient, oauthToken: String): GithubClient = {
    new GithubClient(
      wsClient,
      baseUrl = "https://api.github.com",
      defaultHeaders = Seq(
        ("Authorization" -> s"token $oauthToken"),
      ),
    )
  }

  def parseName(value: String): Name = {
    if (value.trim.isEmpty) {
      Name()
    } else {
      value.trim.split("\\s+").toList match {
        case Nil => Name()
        case first :: Nil => Name(first = Some(first))
        case first :: last :: Nil => Name(first = Some(first), last = Some(last))
        case first :: multiple => Name(first = Some(first), last = Some(multiple.mkString(" ")))
      }
    }
  }

}

abstract class Github {

  /** Fetches the contents of the file at the specified path from the given repository. Returns None if the file is not
    * found.
    *
    * @param path
    *   e.g. "build.sbt", "project/plugins.sbt", etc.
    */
  def file(
    user: UserReference,
    projectUri: String,
    path: String,
    branch: String,
  )(implicit
    ec: ExecutionContext,
  ): Future[Option[String]]

  /** Given an auth validation code, pings the github UI to access the user data, upserts that user with the dependency
    * database, and returns the user (or a list of errors).
    *
    * @param code
    *   The oauth authorization code from github
    */
  def getUserFromCode(usersDao: UsersDao, githubUsersDao: GithubUsersDao, tokensDao: TokensDao, code: String)(implicit
    ec: ExecutionContext,
  ): Future[Either[Seq[String], User]] = {
    getGithubUserFromCode(code).map {
      case Left(errors) => Left(errors)
      case Right(githubUserWithToken) => {
        val userResult: Either[Seq[String], User] = usersDao.findByGithubUserId(githubUserWithToken.githubId) match {
          case Some(user) => {
            Right(user)
          }
          case None => {
            githubUserWithToken.emails.headOption flatMap { email =>
              usersDao.findByEmail(email)
            } match {
              case Some(user) => {
                Right(user)
              }
              case None => {
                usersDao.create(
                  createdBy = None,
                  form = UserForm(
                    email = githubUserWithToken.emails.headOption,
                    name = githubUserWithToken.name.map(GithubHelper.parseName(_)),
                  ),
                )
              }
            }
          }
        }

        userResult match {
          case Left(errors) => {
            Left(errors)
          }
          case Right(user) => {
            githubUsersDao.upsertById(
              createdBy = None,
              form = GithubUserForm(
                userId = user.id,
                githubUserId = githubUserWithToken.githubId,
                login = githubUserWithToken.login,
              ),
            )

            tokensDao.setLatestByTag(
              createdBy = UserReference(id = user.id),
              form = InternalTokenForm.GithubOauth(
                userId = user.id,
                token = githubUserWithToken.token,
              ),
            )

            Right(user)
          }
        }
      }
    }
  }

  /** Fetches github user from an oauth code
    */
  def getGithubUserFromCode(code: String)(implicit ec: ExecutionContext): Future[Either[Seq[String], GithubUserData]]

  def githubRepos(user: UserReference, page: Long = 1)(implicit ec: ExecutionContext): Future[Seq[GithubRepository]]

  /** Recursively calls the github API until we either:
    *   - consume all records
    *   - meet the specified limit/offset
    */
  def repositories(
    user: UserReference,
    offset: Long,
    limit: Long,
    resultsSoFar: Seq[GithubRepository] = Nil,
    page: Long = 1, // internal parameter
  )(
    acceptsFilter: GithubRepository => Boolean = { _ => true },
  )(implicit
    ec: ExecutionContext,
  ): Future[Seq[GithubRepository]] = {
    githubRepos(user, page).flatMap { thisPage =>
      if (thisPage.isEmpty) {
        Future {
          resultsSoFar.drop(offset.toInt).take(limit.toInt)
        }
      } else {
        val all = resultsSoFar ++ thisPage.filter { acceptsFilter(_) }
        if (all.size >= offset + limit) {
          Future {
            all.drop(offset.toInt).take(limit.toInt)
          }
        } else {
          repositories(user, offset, limit, all, page + 1)(acceptsFilter)
        }
      }
    }
  }

  /** For this user, returns the oauth token if available
    */
  def oauthToken(user: UserReference): Option[String]

}

class DefaultGithub @Inject() (wsClient: WSClient, config: Config, tokensDao: TokensDao) extends Github {

  private[this] lazy val clientId = config.requiredString("github.dependency.client.id")
  private[this] lazy val clientSecret = config.requiredString("github.dependency.client.secret")

  private[this] lazy val oauthClient = new GithubOauthClient(
    wsClient,
    baseUrl = "https://github.com",
    defaultHeaders = Seq(
      "Accept" -> "application/json",
    ),
  )

  override def getGithubUserFromCode(
    code: String,
  )(implicit ec: ExecutionContext): Future[Either[Seq[String], GithubUserData]] = {
    val form = AccessTokenForm(
      clientId = clientId,
      clientSecret = clientSecret,
      code = code,
    )
    oauthClient.accessTokens
      .postAccessToken(
        form,
      )
      .flatMap { response =>
        val client = GithubHelper.apiClient(wsClient, response.accessToken)
        for {
          githubUser <- client.users.getUser()
          emails <- client.userEmails.get()
        } yield {
          // put primary first
          val sortedEmailAddresses = (emails.filter(_.primary) ++ emails.filter(!_.primary)).map(_.email)

          Right(
            GithubUserData(
              githubId = githubUser.id,
              login = githubUser.login,
              token = response.accessToken,
              emails = sortedEmailAddresses,
              name = githubUser.name,
              avatarUrl = githubUser.avatarUrl,
            ),
          )
        }
      }
  }

  /** Fetches one page of repositories from the Github API
    */
  override def githubRepos(user: UserReference, page: Long = 1)(implicit
    ec: ExecutionContext,
  ): Future[Seq[GithubRepository]] = {
    oauthToken(user) match {
      case None => Future { Nil }
      case Some(token) => {
        GithubHelper.apiClient(wsClient, token).repositories.getUserAndRepos(page)
      }
    }
  }

  override def oauthToken(user: UserReference): Option[String] = {
    tokensDao.getCleartextGithubOauthTokenByUserId(user.id)
  }

  override def file(
    user: UserReference,
    projectUri: String,
    path: String,
    branch: String,
  )(implicit
    ec: ExecutionContext,
  ): Future[Option[String]] = {
    GithubUtil.parseUri(projectUri) match {
      case Left(error) => {
        sys.error(error)
      }
      case Right(repo) => {
        oauthToken(user) match {
          case None =>
            Future {
              None
            }
          case Some(token) => {
            GithubHelper
              .apiClient(wsClient, token)
              .contents
              .getContentsByPath(
                owner = repo.owner,
                repo = repo.project,
                path = path,
                ref = branch,
              )
              .map { contents =>
                Some(GithubUtil.toText(contents))
              }
              .recover {
                case UnitResponse(404) => {
                  None
                }
              }
          }
        }
      }
    }
  }

}

class MockGithub() extends Github {

  override def getGithubUserFromCode(
    code: String,
  )(implicit ec: ExecutionContext): Future[Either[Seq[String], GithubUserData]] = {
    Future {
      MockGithubData.getUserByCode(code) match {
        case None => Left(Seq("Invalid access code"))
        case Some(u) => Right(u)
      }
    }
  }

  override def githubRepos(user: UserReference, page: Long = 1)(implicit
    ec: ExecutionContext,
  ): Future[Seq[GithubRepository]] = {
    Future {
      MockGithubData.repositories(user)
    }
  }

  override def oauthToken(user: UserReference): Option[String] = {
    MockGithubData.getToken(user)
  }

  override def file(
    user: UserReference,
    projectUri: String,
    path: String,
    branch: String,
  )(implicit
    ec: ExecutionContext,
  ): Future[Option[String]] = {
    Future {
      MockGithubData.getFile(projectUri, path)
    }
  }

}

object MockGithubData {

  private[this] var githubUserByCodes = scala.collection.mutable.Map[String, GithubUserData]()
  private[this] var userTokens = scala.collection.mutable.Map[String, String]()
  private[this] var repositories = scala.collection.mutable.Map[String, GithubRepository]()
  private[this] var files = scala.collection.mutable.Map[String, String]()

  def addUser(githubUser: GithubUser, code: String, token: Option[String] = None): Unit = {
    githubUserByCodes +== (
      code -> GithubUserData(
        githubId = githubUser.id,
        login = githubUser.login,
        token = token.getOrElse(IdGenerator("tok").randomId()),
        emails = Seq(githubUser.email).flatten,
        name = githubUser.name,
        avatarUrl = githubUser.avatarUrl,
      )
    )
  }

  def getUserByCode(code: String): Option[GithubUserData] = {
    githubUserByCodes.lift(code)
  }

  def addUserOauthToken(token: String, user: UserReference): Unit = {
    userTokens +== (user.id -> token)
  }

  def getToken(user: UserReference): Option[String] = {
    userTokens.lift(user.id)
  }

  def addRepository(user: UserReference, repository: GithubRepository) = {
    repositories +== (user.id -> repository)
  }

  def repositories(user: UserReference): Seq[GithubRepository] = {
    repositories.lift(user.id) match {
      case None => Nil
      case Some(repo) => Seq(repo)
    }
  }

  def addFile(
    projectUri: String,
    path: String,
    contents: String,
  ): Unit = {
    files +== (s"${projectUri}.$path" -> contents)
  }

  def getFile(
    projectUri: String,
    path: String,
  ): Option[String] = {
    files.get(s"${projectUri}.$path")
  }

}
