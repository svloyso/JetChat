package github

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Singleton

import api._
import github.GitHubIntegration.GitHubMessageHandler
import models.api.IntegrationToken
import models.{IntegrationTopic, IntegrationUpdate}
import org.apache.commons.lang3.StringEscapeUtils
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.JsValue
import play.api.libs.ws.WSAuthScheme.BASIC
import play.api.libs.ws.{WS, WSResponse}
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Request, Result}
import play.api.{Play, http}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
  * @author Alefas
  * @since  15/09/15
  */
@Singleton
class GitHubIntegration extends Integration {
  override def id: String = GitHubIntegration.ID

  override def name: String = GitHubIntegration.ID

  override def hookHandler: Option[HookHandler] = None

  override def authentificator: OAuthAuthentificator = new OAuthAuthentificator {
    override def integrationId: String = id

    override def disable(token: String): Future[Boolean] = {
      WS.url(s"https://api.github.com/applications/$clientId/tokens/$token")(Play.current).
        withAuth(clientId, clientSecret, BASIC).delete().map { _ => true}
    }

    override def token(redirectUri: String, code: String): Future[String] = {
      val tokenResponse = WS.url("https://github.com/login/oauth/access_token")(Play.current).
        withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON).
        post(Map("client_id" -> Seq(clientId),
          "client_secret" -> Seq(clientSecret),
          "code" -> Seq(code),
          "redirect_uri" -> Seq(redirectUri)))

      tokenResponse.flatMap { response =>
        (response.json \ "access_token").asOpt[String].fold(Future.failed[String](new IllegalStateException("Sod off!"))) { accessToken =>
          Future.successful(accessToken)
        }
      }
    }

    override def enable(redirectUrl: Option[String], state: String)(implicit request: Request[AnyContent]): Future[Result] = {
      val callbackUrl = Utils.callbackUrl(id, redirectUrl)
      val scope = "user, repo, gist, read:org"
      Future.successful(Redirect(GitHubIntegration.getAuthorizationUrl(callbackUrl, scope, state, clientId)))
    }
  }

  override def userHandler: UserHandler = new UserHandler {
    private def info(token: String, field: String, login: Option[String] = None): Future[Option[String]] = {
      val userUrl = login.map(l => s"https://api.github.com/users/$l").getOrElse(s"https://api.github.com/user")
      GitHubIntegration.askAPI(userUrl, token).map { response =>
        (response.json \ field).asOpt[String]
      }
    }

    override def avatarUrl(token: String, login: Option[String] = None): Future[Option[String]] =
      info(token, "avatar_url", login)
    override def name(token: String, login: Option[String] = None): Future[Option[String]] =
      info(token, "name", login)
    override def login(token: String): Future[String] =
      info(token, "login").map(_.get)

    override def groupName(token: String, groupId: String): Future[String] = Future {
      groupId //for GitHub its pretty normal. See for example Gitter.
    }
  }

  override def messageHandler: MessageHandler = new GitHubMessageHandler
}

object GitHubIntegration {
  val ID = "GitHub"

  val sincePeriod = 1000 * 60 * 60 * 24 * 7

  private def getAuthorizationUrl(redirectUri: String, scope: String, state: String, clientId: String): String = {
    s"https://github.com/login/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&scope=$scope&state=$state"
  }

  def askAPI(url: String, token: String): Future[WSResponse] = {
    WS.url(url)(Play.current).withQueryString("access_token" -> token).
      withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON).get()
  }

  class GitHubMessageHandler extends MessageHandler {
    private val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

    override def collectMessages(integrationToken: IntegrationToken): Future[CollectedMessages] = {
      def eventText(json: JsValue): String = {
        (json \ "event").asOpt[String] match {
          case Some("closed") =>
            (json \ "commitId").asOpt[String] match {
              case Some(commitId) => s"closed by $commitId"
              case _ => "closed"
            }
          case Some("referenced") =>
            val commitId = (json \ "commit_id").as[String]
            s"referenced from $commitId"
          case Some("assigned") =>
            val assignee = (json \ "assignee" \ "login").as[String]
            s"assigned to $assignee"
          case Some("unassigned") =>
            val assignee = (json \ "assignee" \ "login").as[String]
            s"unassigned from $assignee"
          case Some("labeled") =>
            val name = (json \ "label" \ "name").as[String]
            s"labeled: $name"
          case Some("unlabeled") =>
            val name = (json \ "label" \ "name").as[String]
            s"unlabeled: $name"
          case Some("milestoned") =>
            val title = (json \ "milestone" \ "title").as[String]
            s"milestoned: $title"
          case Some("demilestoned") =>
            val title = (json \ "milestone" \ "title").as[String]
            s"demilestoned: $title"
          case Some("renamed") =>
            val from = (json \ "rename:" \ "from").as[String]
            val to = (json \ "rename:" \ "to").as[String]
            s"""renamed from:
                |  $from
                |
               |        to:
                |  $to
             """.stripMargin
          case Some("head_ref_deleted") => "The pull request’s branch was deleted."
          case Some("head_ref_restored") => "The pull request’s branch was restored."
          case Some(other) => other
          case None => (json \ "description").as[String]
        }
      }

      val since = dateFormat.format(new Date(System.currentTimeMillis() - sincePeriod))
      val notificationsUrl = s"https://api.github.com/notifications?all=true&since=$since"
      askAPI(notificationsUrl, integrationToken.token).flatMap { response =>
        val pollInterval: Int = response.header("X-Poll-Interval") match {
          case Some(p) =>
            try {
              p.toInt
            } catch { case e: Throwable => 60 }
          case _ => 60
        }
        val seq: Seq[JsValue] = response.json.as[Seq[JsValue]]
        Future.sequence(seq.map { value =>
          val groupId = (value \ "repository" \ "full_name").as[String]
          val subject = value \ "subject"
          val title = (subject \ "title").as[String]
          val tp = (subject \ "type").as[String]
          val url = (subject \ "url").as[String]
          val index = url.lastIndexOf('/')
          val lastPart = url.substring(index + 1)
          val topicTitle = tp match {
            case "Commit" => s"$title (${lastPart.substring(0, 7)})"
            case "Issue" => s"#$lastPart $title"
            case "PullRequest" => s"PR #$lastPart $title"
          }

          val topicId = s"$tp/$lastPart"

          askAPI(url, integrationToken.token).flatMap { response =>
            val json = response.json
            val topicAuthor = tp match {
              case "Commit" => (json \ "author" \ "login").as[String]
              case _ => (json \ "user" \ "login").as[String]
            }
            val topicText = tp match {
              case "Commit" => (json \ "commit" \ "message").as[String]
              case _ => (json \ "title").as[String]
            }
            val topicTimestamp = tp match {
              case "Commit" => new Timestamp(dateFormat.parse((json \ "commit" \ "author" \ "date").as[String]).getTime)
              case _ => new Timestamp(dateFormat.parse((json \ "created_at").as[String]).getTime)
            }
            val integrationTopic = IntegrationTopic(ID, topicId, groupId, integrationToken.userId, topicAuthor, topicTimestamp, topicText, topicTitle)

            val commentsFuture = askAPI((json \ "comments_url").as[String], integrationToken.token).map { response =>
              val json = response.json
              val comments = json.asOpt[Seq[JsValue]].getOrElse(Seq(json.as[JsValue]))
              comments.map { value =>
                val commentId = (value \ "id").as[Long]
                val userId = (value \ "user" \ "login").as[String]
                val timestamp = new Timestamp(dateFormat.parse((value \ "created_at").as[String]).getTime)
                val text = (value \ "body").as[String]
                IntegrationUpdate(0, ID, Some(commentId.toString), groupId, topicId, integrationToken.userId, userId, timestamp, text)
              }
            }
            ((json \ "events_url").asOpt[String].orElse((json \ "statuses_url").asOpt[String]) match {
              case Some(events_url) =>
                commentsFuture.zip {
                  askAPI(events_url, integrationToken.token).map { response =>
                    val json = response.json
                    val events = json.asOpt[Seq[JsValue]].getOrElse(Seq(json.as[JsValue]))
                    events.collect {
                      case v if !Set(Option("mentioned"), Option("subscribed")).contains((v \ "event").asOpt[String]) =>
                        val eventId = (v \ "id").as[Long]
                        val userId = (v \ "actor" \ "login").asOpt[String].getOrElse(
                          (v \ "creator" \ "login").as[String]
                        )
                        val timestamp = new Timestamp(dateFormat.parse((v \ "created_at").as[String]).getTime)
                        val text = eventText(v)
                        IntegrationUpdate(0, ID, Some(eventId.toString), groupId, topicId, integrationToken.userId, userId, timestamp, text)
                    }
                  }
                }.map { case (comments, events) => comments ++ events }
              case _ => commentsFuture
            }).map(integrationTopic -> _)
          }
        }).map(_.toMap).map(CollectedMessages(_, pollInterval.seconds))
      }
    }

    override def sendMessage(integrationToken: IntegrationToken, groupId: String, topicId: String,
                             message: SentMessage, messageId: Long): Future[Option[IntegrationUpdate]] = {
      message match {
        case TopicComment(text) =>
          val Commit = """Commit/(.*)""".r
          val Issue = """Issue/(.*)""".r
          val PullRequest = """PullRequest/(.*)""".r

          def update(issueType: String, id: String): Future[Option[IntegrationUpdate]] = {
            WS.url(s"https://api.github.com/repos/$groupId/$issueType/$id/comments")(Play.current).
              withQueryString("access_token" -> integrationToken.token).
              withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON,
                HeaderNames.CONTENT_TYPE -> MimeTypes.JSON
              ).post(
              s"""
                 |{
                 |  "body": "${StringEscapeUtils.escapeJson(text)}"
                 |}
              """.stripMargin.trim).map { response =>
              if (response.status == http.Status.CREATED) {
                val json = response.json
                val value = json.as[JsValue]
                val commentId = (value \ "id").as[Long]
                val userId = (value \ "user" \ "login").as[String]
                val timestamp = new Timestamp(dateFormat.parse((value \ "created_at").as[String]).getTime)
                val text = (value \ "body").as[String]
                Some(IntegrationUpdate(messageId, ID, Some(commentId.toString), groupId, topicId, integrationToken.userId, userId, timestamp, text))
              } else None
            }
          }

          topicId match {
            case Commit(hash) => Future(None)//todo: working with commits
            case Issue(issueId) => update("issues", issueId)
            case PullRequest(pullId) => update("pulls", pullId)
          }
        case _ => Future(None)
      }
    }
  }
}
