/**
 * Generated by API Builder - https://www.apibuilder.io
 * Service version: 0.3.45
 * apibuilder app.apibuilder.io/flow/github/latest/anorm_2_8_parsers
 */
package io.flow.github.v0.anorm.conversions {

  import anorm.{Column, MetaDataItem, TypeDoesNotMatch}
  import play.api.libs.json.{JsArray, JsObject, JsValue}
  import scala.util.{Failure, Success, Try}
  import play.api.libs.json.JodaReads._

  /**
    * Conversions to collections of objects using JSON.
    */
  object Util {

    def parser[T](
      f: play.api.libs.json.JsValue => T
    ) = anorm.Column.nonNull { (value, meta) =>
      val MetaDataItem(columnName, nullable, clazz) = meta
      value match {
        case json: org.postgresql.util.PGobject => parseJson(f, columnName.qualified, json.getValue)
        case json: java.lang.String => parseJson(f, columnName.qualified, json)
        case _=> {
          Left(
            TypeDoesNotMatch(
              s"Column[${columnName.qualified}] error converting $value to Json. Expected instance of type[org.postgresql.util.PGobject] and not[${value.asInstanceOf[AnyRef].getClass}]"
            )
          )
        }


      }
    }

    private[this] def parseJson[T](f: play.api.libs.json.JsValue => T, columnName: String, value: String) = {
      Try {
        f(
          play.api.libs.json.Json.parse(value)
        )
      } match {
        case Success(result) => Right(result)
        case Failure(ex) => Left(
          TypeDoesNotMatch(
            s"Column[$columnName] error parsing json $value: $ex"
          )
        )
      }
    }

  }

  object Types {
    import io.flow.github.v0.models.json._
    implicit val columnToSeqGithubContentsType: Column[Seq[_root_.io.flow.github.v0.models.ContentsType]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.ContentsType]] }
    implicit val columnToMapGithubContentsType: Column[Map[String, _root_.io.flow.github.v0.models.ContentsType]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.ContentsType]] }
    implicit val columnToSeqGithubEncoding: Column[Seq[_root_.io.flow.github.v0.models.Encoding]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.Encoding]] }
    implicit val columnToMapGithubEncoding: Column[Map[String, _root_.io.flow.github.v0.models.Encoding]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.Encoding]] }
    implicit val columnToSeqGithubHookEvent: Column[Seq[_root_.io.flow.github.v0.models.HookEvent]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.HookEvent]] }
    implicit val columnToMapGithubHookEvent: Column[Map[String, _root_.io.flow.github.v0.models.HookEvent]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.HookEvent]] }
    implicit val columnToSeqGithubNodeType: Column[Seq[_root_.io.flow.github.v0.models.NodeType]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.NodeType]] }
    implicit val columnToMapGithubNodeType: Column[Map[String, _root_.io.flow.github.v0.models.NodeType]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.NodeType]] }
    implicit val columnToSeqGithubOwnerType: Column[Seq[_root_.io.flow.github.v0.models.OwnerType]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.OwnerType]] }
    implicit val columnToMapGithubOwnerType: Column[Map[String, _root_.io.flow.github.v0.models.OwnerType]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.OwnerType]] }
    implicit val columnToSeqGithubVisibility: Column[Seq[_root_.io.flow.github.v0.models.Visibility]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.Visibility]] }
    implicit val columnToMapGithubVisibility: Column[Map[String, _root_.io.flow.github.v0.models.Visibility]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.Visibility]] }
    implicit val columnToSeqGithubBlob: Column[Seq[_root_.io.flow.github.v0.models.Blob]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.Blob]] }
    implicit val columnToMapGithubBlob: Column[Map[String, _root_.io.flow.github.v0.models.Blob]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.Blob]] }
    implicit val columnToSeqGithubBlobCreated: Column[Seq[_root_.io.flow.github.v0.models.BlobCreated]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.BlobCreated]] }
    implicit val columnToMapGithubBlobCreated: Column[Map[String, _root_.io.flow.github.v0.models.BlobCreated]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.BlobCreated]] }
    implicit val columnToSeqGithubBlobForm: Column[Seq[_root_.io.flow.github.v0.models.BlobForm]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.BlobForm]] }
    implicit val columnToMapGithubBlobForm: Column[Map[String, _root_.io.flow.github.v0.models.BlobForm]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.BlobForm]] }
    implicit val columnToSeqGithubCommit: Column[Seq[_root_.io.flow.github.v0.models.Commit]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.Commit]] }
    implicit val columnToMapGithubCommit: Column[Map[String, _root_.io.flow.github.v0.models.Commit]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.Commit]] }
    implicit val columnToSeqGithubCommitForm: Column[Seq[_root_.io.flow.github.v0.models.CommitForm]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.CommitForm]] }
    implicit val columnToMapGithubCommitForm: Column[Map[String, _root_.io.flow.github.v0.models.CommitForm]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.CommitForm]] }
    implicit val columnToSeqGithubCommitResponse: Column[Seq[_root_.io.flow.github.v0.models.CommitResponse]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.CommitResponse]] }
    implicit val columnToMapGithubCommitResponse: Column[Map[String, _root_.io.flow.github.v0.models.CommitResponse]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.CommitResponse]] }
    implicit val columnToSeqGithubCommitSummary: Column[Seq[_root_.io.flow.github.v0.models.CommitSummary]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.CommitSummary]] }
    implicit val columnToMapGithubCommitSummary: Column[Map[String, _root_.io.flow.github.v0.models.CommitSummary]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.CommitSummary]] }
    implicit val columnToSeqGithubContents: Column[Seq[_root_.io.flow.github.v0.models.Contents]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.Contents]] }
    implicit val columnToMapGithubContents: Column[Map[String, _root_.io.flow.github.v0.models.Contents]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.Contents]] }
    implicit val columnToSeqGithubCreateTreeForm: Column[Seq[_root_.io.flow.github.v0.models.CreateTreeForm]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.CreateTreeForm]] }
    implicit val columnToMapGithubCreateTreeForm: Column[Map[String, _root_.io.flow.github.v0.models.CreateTreeForm]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.CreateTreeForm]] }
    implicit val columnToSeqGithubCreateTreeResponse: Column[Seq[_root_.io.flow.github.v0.models.CreateTreeResponse]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.CreateTreeResponse]] }
    implicit val columnToMapGithubCreateTreeResponse: Column[Map[String, _root_.io.flow.github.v0.models.CreateTreeResponse]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.CreateTreeResponse]] }
    implicit val columnToSeqGithubError: Column[Seq[_root_.io.flow.github.v0.models.Error]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.Error]] }
    implicit val columnToMapGithubError: Column[Map[String, _root_.io.flow.github.v0.models.Error]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.Error]] }
    implicit val columnToSeqGithubGithubObject: Column[Seq[_root_.io.flow.github.v0.models.GithubObject]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.GithubObject]] }
    implicit val columnToMapGithubGithubObject: Column[Map[String, _root_.io.flow.github.v0.models.GithubObject]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.GithubObject]] }
    implicit val columnToSeqGithubHook: Column[Seq[_root_.io.flow.github.v0.models.Hook]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.Hook]] }
    implicit val columnToMapGithubHook: Column[Map[String, _root_.io.flow.github.v0.models.Hook]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.Hook]] }
    implicit val columnToSeqGithubHookConfig: Column[Seq[_root_.io.flow.github.v0.models.HookConfig]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.HookConfig]] }
    implicit val columnToMapGithubHookConfig: Column[Map[String, _root_.io.flow.github.v0.models.HookConfig]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.HookConfig]] }
    implicit val columnToSeqGithubHookForm: Column[Seq[_root_.io.flow.github.v0.models.HookForm]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.HookForm]] }
    implicit val columnToMapGithubHookForm: Column[Map[String, _root_.io.flow.github.v0.models.HookForm]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.HookForm]] }
    implicit val columnToSeqGithubNode: Column[Seq[_root_.io.flow.github.v0.models.Node]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.Node]] }
    implicit val columnToMapGithubNode: Column[Map[String, _root_.io.flow.github.v0.models.Node]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.Node]] }
    implicit val columnToSeqGithubNodeForm: Column[Seq[_root_.io.flow.github.v0.models.NodeForm]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.NodeForm]] }
    implicit val columnToMapGithubNodeForm: Column[Map[String, _root_.io.flow.github.v0.models.NodeForm]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.NodeForm]] }
    implicit val columnToSeqGithubPerson: Column[Seq[_root_.io.flow.github.v0.models.Person]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.Person]] }
    implicit val columnToMapGithubPerson: Column[Map[String, _root_.io.flow.github.v0.models.Person]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.Person]] }
    implicit val columnToSeqGithubPullRequest: Column[Seq[_root_.io.flow.github.v0.models.PullRequest]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.PullRequest]] }
    implicit val columnToMapGithubPullRequest: Column[Map[String, _root_.io.flow.github.v0.models.PullRequest]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.PullRequest]] }
    implicit val columnToSeqGithubPullRequestForm: Column[Seq[_root_.io.flow.github.v0.models.PullRequestForm]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.PullRequestForm]] }
    implicit val columnToMapGithubPullRequestForm: Column[Map[String, _root_.io.flow.github.v0.models.PullRequestForm]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.PullRequestForm]] }
    implicit val columnToSeqGithubPullRequestHead: Column[Seq[_root_.io.flow.github.v0.models.PullRequestHead]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.PullRequestHead]] }
    implicit val columnToMapGithubPullRequestHead: Column[Map[String, _root_.io.flow.github.v0.models.PullRequestHead]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.PullRequestHead]] }
    implicit val columnToSeqGithubRef: Column[Seq[_root_.io.flow.github.v0.models.Ref]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.Ref]] }
    implicit val columnToMapGithubRef: Column[Map[String, _root_.io.flow.github.v0.models.Ref]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.Ref]] }
    implicit val columnToSeqGithubRefForm: Column[Seq[_root_.io.flow.github.v0.models.RefForm]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.RefForm]] }
    implicit val columnToMapGithubRefForm: Column[Map[String, _root_.io.flow.github.v0.models.RefForm]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.RefForm]] }
    implicit val columnToSeqGithubRefUpdateForm: Column[Seq[_root_.io.flow.github.v0.models.RefUpdateForm]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.RefUpdateForm]] }
    implicit val columnToMapGithubRefUpdateForm: Column[Map[String, _root_.io.flow.github.v0.models.RefUpdateForm]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.RefUpdateForm]] }
    implicit val columnToSeqGithubRepository: Column[Seq[_root_.io.flow.github.v0.models.Repository]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.Repository]] }
    implicit val columnToMapGithubRepository: Column[Map[String, _root_.io.flow.github.v0.models.Repository]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.Repository]] }
    implicit val columnToSeqGithubTag: Column[Seq[_root_.io.flow.github.v0.models.Tag]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.Tag]] }
    implicit val columnToMapGithubTag: Column[Map[String, _root_.io.flow.github.v0.models.Tag]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.Tag]] }
    implicit val columnToSeqGithubTagForm: Column[Seq[_root_.io.flow.github.v0.models.TagForm]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.TagForm]] }
    implicit val columnToMapGithubTagForm: Column[Map[String, _root_.io.flow.github.v0.models.TagForm]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.TagForm]] }
    implicit val columnToSeqGithubTagSummary: Column[Seq[_root_.io.flow.github.v0.models.TagSummary]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.TagSummary]] }
    implicit val columnToMapGithubTagSummary: Column[Map[String, _root_.io.flow.github.v0.models.TagSummary]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.TagSummary]] }
    implicit val columnToSeqGithubTagger: Column[Seq[_root_.io.flow.github.v0.models.Tagger]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.Tagger]] }
    implicit val columnToMapGithubTagger: Column[Map[String, _root_.io.flow.github.v0.models.Tagger]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.Tagger]] }
    implicit val columnToSeqGithubTree: Column[Seq[_root_.io.flow.github.v0.models.Tree]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.Tree]] }
    implicit val columnToMapGithubTree: Column[Map[String, _root_.io.flow.github.v0.models.Tree]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.Tree]] }
    implicit val columnToSeqGithubTreeForm: Column[Seq[_root_.io.flow.github.v0.models.TreeForm]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.TreeForm]] }
    implicit val columnToMapGithubTreeForm: Column[Map[String, _root_.io.flow.github.v0.models.TreeForm]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.TreeForm]] }
    implicit val columnToSeqGithubTreeResult: Column[Seq[_root_.io.flow.github.v0.models.TreeResult]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.TreeResult]] }
    implicit val columnToMapGithubTreeResult: Column[Map[String, _root_.io.flow.github.v0.models.TreeResult]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.TreeResult]] }
    implicit val columnToSeqGithubTreeSummary: Column[Seq[_root_.io.flow.github.v0.models.TreeSummary]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.TreeSummary]] }
    implicit val columnToMapGithubTreeSummary: Column[Map[String, _root_.io.flow.github.v0.models.TreeSummary]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.TreeSummary]] }
    implicit val columnToSeqGithubUnprocessableEntity: Column[Seq[_root_.io.flow.github.v0.models.UnprocessableEntity]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.UnprocessableEntity]] }
    implicit val columnToMapGithubUnprocessableEntity: Column[Map[String, _root_.io.flow.github.v0.models.UnprocessableEntity]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.UnprocessableEntity]] }
    implicit val columnToSeqGithubUser: Column[Seq[_root_.io.flow.github.v0.models.User]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.User]] }
    implicit val columnToMapGithubUser: Column[Map[String, _root_.io.flow.github.v0.models.User]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.User]] }
    implicit val columnToSeqGithubUserEmail: Column[Seq[_root_.io.flow.github.v0.models.UserEmail]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.UserEmail]] }
    implicit val columnToMapGithubUserEmail: Column[Map[String, _root_.io.flow.github.v0.models.UserEmail]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.UserEmail]] }
    implicit val columnToSeqGithubUserOrg: Column[Seq[_root_.io.flow.github.v0.models.UserOrg]] = Util.parser { _.as[Seq[_root_.io.flow.github.v0.models.UserOrg]] }
    implicit val columnToMapGithubUserOrg: Column[Map[String, _root_.io.flow.github.v0.models.UserOrg]] = Util.parser { _.as[Map[String, _root_.io.flow.github.v0.models.UserOrg]] }
  }

  object Standard {
    implicit val columnToJsObject: Column[play.api.libs.json.JsObject] = Util.parser { _.as[play.api.libs.json.JsObject] }
    implicit val columnToJsValue: Column[play.api.libs.json.JsValue] = Util.parser { _.as[play.api.libs.json.JsValue] }
    implicit val columnToSeqBoolean: Column[Seq[Boolean]] = Util.parser { _.as[Seq[Boolean]] }
    implicit val columnToMapBoolean: Column[Map[String, Boolean]] = Util.parser { _.as[Map[String, Boolean]] }
    implicit val columnToSeqDouble: Column[Seq[Double]] = Util.parser { _.as[Seq[Double]] }
    implicit val columnToMapDouble: Column[Map[String, Double]] = Util.parser { _.as[Map[String, Double]] }
    implicit val columnToSeqInt: Column[Seq[Int]] = Util.parser { _.as[Seq[Int]] }
    implicit val columnToMapInt: Column[Map[String, Int]] = Util.parser { _.as[Map[String, Int]] }
    implicit val columnToSeqLong: Column[Seq[Long]] = Util.parser { _.as[Seq[Long]] }
    implicit val columnToMapLong: Column[Map[String, Long]] = Util.parser { _.as[Map[String, Long]] }
    implicit val columnToSeqLocalDate: Column[Seq[_root_.org.joda.time.LocalDate]] = Util.parser { _.as[Seq[_root_.org.joda.time.LocalDate]] }
    implicit val columnToMapLocalDate: Column[Map[String, _root_.org.joda.time.LocalDate]] = Util.parser { _.as[Map[String, _root_.org.joda.time.LocalDate]] }
    implicit val columnToSeqDateTime: Column[Seq[_root_.org.joda.time.DateTime]] = Util.parser { _.as[Seq[_root_.org.joda.time.DateTime]] }
    implicit val columnToMapDateTime: Column[Map[String, _root_.org.joda.time.DateTime]] = Util.parser { _.as[Map[String, _root_.org.joda.time.DateTime]] }
    implicit val columnToSeqBigDecimal: Column[Seq[BigDecimal]] = Util.parser { _.as[Seq[BigDecimal]] }
    implicit val columnToMapBigDecimal: Column[Map[String, BigDecimal]] = Util.parser { _.as[Map[String, BigDecimal]] }
    implicit val columnToSeqJsObject: Column[Seq[_root_.play.api.libs.json.JsObject]] = Util.parser { _.as[Seq[_root_.play.api.libs.json.JsObject]] }
    implicit val columnToMapJsObject: Column[Map[String, _root_.play.api.libs.json.JsObject]] = Util.parser { _.as[Map[String, _root_.play.api.libs.json.JsObject]] }
    implicit val columnToSeqJsValue: Column[Seq[_root_.play.api.libs.json.JsValue]] = Util.parser { _.as[Seq[_root_.play.api.libs.json.JsValue]] }
    implicit val columnToMapJsValue: Column[Map[String, _root_.play.api.libs.json.JsValue]] = Util.parser { _.as[Map[String, _root_.play.api.libs.json.JsValue]] }
    implicit val columnToSeqString: Column[Seq[String]] = Util.parser { _.as[Seq[String]] }
    implicit val columnToMapString: Column[Map[String, String]] = Util.parser { _.as[Map[String, String]] }
    implicit val columnToSeqUUID: Column[Seq[_root_.java.util.UUID]] = Util.parser { _.as[Seq[_root_.java.util.UUID]] }
    implicit val columnToMapUUID: Column[Map[String, _root_.java.util.UUID]] = Util.parser { _.as[Map[String, _root_.java.util.UUID]] }
  }

}