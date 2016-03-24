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
import play.api.{Logger, Play, http}

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

    override def logout(token: String): Future[Boolean] = {
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

    override def auth(redirectUrl: Option[String], state: String)(implicit request: Request[AnyContent]): Future[Result] = {
      val callbackUrl = Utils.callbackUrl(id, redirectUrl)
      val scope = "user, repo, gist, read:org"
      Future.successful(Redirect(GitHubIntegration.getAuthorizationUrl(callbackUrl, scope, state, clientId)))
    }
  }

  override def userHandler: UserHandler = new UserHandler {
    private def info(token: String, field: String, login: Option[String] = None): Future[Option[String]] = {
      val userUrl = login.map(l => s"https://api.github.com/users/$l").getOrElse(s"https://api.github.com/user")
      GitHubIntegration.askAPI(userUrl, token).map { result =>
        (result.response.json \ field).asOpt[String]
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

  val defaultPollInterval = 60 * 60
  val sincePeriod = 1000 * 60 * 60 * 24 * 14

  private val LOG = Logger.apply(this.getClass)

  private def getAuthorizationUrl(redirectUri: String, scope: String, state: String, clientId: String): String = {
    s"https://github.com/login/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&scope=$scope&state=$state"
  }

  case class APICallResult(successful: Boolean, response: WSResponse, pollInterval: Option[Int])

  def askAPI(url: String, token: String): Future[APICallResult] = {
    LOG.trace("API request for " + token + ": " + url)
    WS.url(url)(Play.current).withQueryString("access_token" -> token).
      withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON).get().map(response =>
      if (response.status == 404) {
        LOG.warn("API request returned 404: " + url)
        APICallResult(successful = false, response, None)
      } else if (response.status == 403) {
        LOG.warn("API request returned 403: " + url)
        val pollInterval = if (response.header("X-RateLimit-Reset").isDefined) {
          val limit = response.header("X-RateLimit-Reset").get
          ((new Date(limit.toLong * 1000L).getTime - new Date().getTime) / 1000).asInstanceOf[Int]
        } else {
          defaultPollInterval
        }
        APICallResult(successful = false, response, Some(pollInterval))
      } else if (response.header("X-Poll-Interval").isDefined) {
        APICallResult(successful = true, response, Some(response.header("X-Poll-Interval").get.toInt))
      } else {
        APICallResult(successful = true, response, None)
      }
    ).recover { case t: Throwable =>
      LOG.error("API request failed for " + token + ": " + url, t)
      throw t
    }
  }

  class GitHubMessageHandler extends MessageHandler {
    private val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

    override def collectMessages(integrationToken: IntegrationToken, since: Option[Long]): Future[CollectedMessages] = {
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
            val from = (json \ "rename" \ "from").as[String]
            val to = (json \ "rename" \ "to").as[String]
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

      def askWithRecover[R](url: String, f: Future[APICallResult] => Future[R], default: => R): Future[R] = {
        f(askAPI(url, integrationToken.token)).recover {
          case t: Throwable =>
            LOG.error(s"Error during $url API check.", t)
            default
        }
      }

      def extractEvents(groupId: String, topicId: String, events_url: String): Future[Option[Seq[IntegrationUpdate]]] = {
        askWithRecover(events_url, _.map { result =>
          if (result.successful) {
            val json = result.response.json
            val events = json.asOpt[Seq[JsValue]].getOrElse(Seq(json.as[JsValue]))
            Some(events.collect {
              case v if !Set(Option("mentioned"), Option("subscribed")).contains((v \ "event").asOpt[String]) =>
                val eventId = (v \ "id").as[Long]
                val userId = (v \ "actor" \ "login").asOpt[String].getOrElse(
                  (v \ "creator" \ "login").as[String]
                )
                val timestamp = new Timestamp(dateFormat.parse((v \ "created_at").as[String]).getTime)
                val text = eventText(v)
                IntegrationUpdate(0, ID, Some(eventId.toString), groupId, topicId, integrationToken.userId, userId, timestamp, text)
            })
          } else {
            None
          }
        }, None)
      }

      def extractComments(groupId: String, topicId: String, comments_url: String): Future[Option[Seq[IntegrationUpdate]]] = {
        askWithRecover(comments_url, _.map { result =>
          if (result.successful) {
            val json = result.response.json
            val comments = json.asOpt[Seq[JsValue]].getOrElse(Seq(json.as[JsValue]))
            Some(comments.map { value =>
              val commentId = (value \ "id").as[Long]
              val userId = (value \ "user" \ "login").as[String]
              val timestamp = new Timestamp(dateFormat.parse((value \ "created_at").as[String]).getTime)
              val text = (value \ "body").as[String]
              IntegrationUpdate(0, ID, Some(commentId.toString), groupId, topicId, integrationToken.userId, userId, timestamp, text)
            })
          } else {
            None
          }
        }, None)
      }

      val defaultSince = System.currentTimeMillis() - sincePeriod
      val sinceDate = dateFormat.format(new Date(since.map(_.max(defaultSince)).getOrElse(defaultSince)))
      val notificationsUrl = s"https://api.github.com/notifications?all=true&since=$sinceDate"

      def extractLatestSince(map: Map[IntegrationTopic, Seq[IntegrationUpdate]]): Long = {
        map.foldLeft(defaultSince) {
          case (since: Long, (topic: IntegrationTopic, updates: Seq[IntegrationUpdate])) =>
            since.max(topic.date.getTime).max(updates.foldLeft(0L) {
              case (l, update) => l.max(update.date.getTime)
            })
        }
      }

      askWithRecover(notificationsUrl, _.flatMap { result =>
        if (result.successful) {
          val seq: Seq[JsValue] = result.response.json.as[Seq[JsValue]]
          Future.sequence(seq.map { value =>
            val groupId = (value \ "repository" \ "full_name").as[String]
            val subject = value \ "subject"
            val title = (subject \ "title").as[String]
            val tp = (subject \ "type").as[String]
            val url = (subject \ "url").as[String]
            val index = url.lastIndexOf('/')
            val lastPart = url.substring(index + 1)
            val topicId = s"$tp/$lastPart"

            askWithRecover(url, _.flatMap { result =>
              if (result.successful) {
                tp match {
                  case "Commit" | "Issue" | "PullRequest" =>
                    val json = result.response.json
                    val topicTitle = tp match {
                      case "Commit" => s"$title (${lastPart.substring(0, 7)})"
                      case "Issue" => s"#$lastPart $title"
                      case "PullRequest" => s"PR #$lastPart $title"
                    }
                    val topicAuthor = tp match {
                      case "Commit" => (json \ "author" \ "login").as[String]
                      case _ => (json \ "user" \ "login").as[String]
                    }
                    val descriptionText = tp match {
                      case "Commit" => (json \ "commit" \ "message").as[String]
                      case _ => (json \ "body").as[String]
                    }
                    val topicTimestamp = tp match {
                      case "Commit" => new Timestamp(dateFormat.parse((json \ "commit" \ "author" \ "date").as[String]).getTime)
                      case _ => new Timestamp(dateFormat.parse((json \ "created_at").as[String]).getTime)
                    }
                    val topicUrl = (json \ "html_url").as[String]
                    val urlUpdate = IntegrationUpdate(0, ID, Some(topicId + "_Url"), groupId, topicId, integrationToken.userId, topicAuthor, topicTimestamp, topicUrl)
                    val descriptionUpdate: Option[IntegrationUpdate] =
                      if (descriptionText.isEmpty) None
                      else Some(IntegrationUpdate(0, ID, Some(topicId + "_Description"), groupId, topicId, integrationToken.userId, topicAuthor, topicTimestamp, descriptionText))

                    val integrationTopic = IntegrationTopic(0, ID, Some(topicId), groupId, integrationToken.userId, topicAuthor, topicTimestamp, topicTitle, topicTitle)

                    val comments_url = (json \ "comments_url").as[String]
                    val commentsFuture = extractComments(groupId, topicId, comments_url)

                    ((json \ "events_url").asOpt[String].orElse((json \ "statuses_url").asOpt[String]) match {
                      case Some(events_url) =>
                        commentsFuture.zip(extractEvents(groupId, topicId, events_url)).map {
                          case (Some(comments), Some(events)) => Some(comments ++ events)
                          case _ => None
                        }
                      case _ => commentsFuture
                    }).map { case Some(x) => Some(integrationTopic -> (Seq(urlUpdate) ++ descriptionUpdate ++ x)) case _ => None }
                  case _ =>
                    LOG.error(s"Unsupported topic title $tp")
                    Future { None }
                }
              } else Future { None }
            }, None)
          }).map(_.flatten.toMap).map(topicToUpdates =>
            CollectedMessages(topicToUpdates, result.pollInterval.getOrElse(60).seconds, extractLatestSince(topicToUpdates)))
          .recover {
            case t: Throwable =>
              LOG.error(s"Error during $notificationsUrl check", t)
              CollectedMessages(Map.empty, result.pollInterval.getOrElse(60).seconds, defaultSince)
          }
        } else {
          Future.successful(CollectedMessages(Map(), result.pollInterval.getOrElse(60).seconds, defaultSince))
        }
      }, CollectedMessages(Map(), 60.seconds, defaultSince))
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
            case PullRequest(pullId) => update("issues", pullId)
          }
        case _ => Future(None)
      }
    }

    override def isNewTopicAvailable: Boolean = true

    override def newTopic(integrationToken: IntegrationToken, groupId: String,
                          message: SentNewTopic): Future[Option[IntegrationTopic]] = { //todo: add url
      message match {
        case NewTopic(text) =>
          WS.url(s"https://api.github.com/repos/$groupId/issues")(Play.current).
            withQueryString("access_token" -> integrationToken.token).
            withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON,
              HeaderNames.CONTENT_TYPE -> MimeTypes.JSON
            ).post(
            s"""
               |{
               |  "title": "${StringEscapeUtils.escapeJson(text)}"
               |}
              """.stripMargin.trim).map { response =>
            if (response.status == http.Status.CREATED) {
              val json = response.json
              val value = json.as[JsValue]
              val id = (value \ "id").as[Long]
              val userId = (value \ "user" \ "login").as[String]
              val timestamp = new Timestamp(dateFormat.parse((value \ "created_at").as[String]).getTime)
              val title = (value \ "title").as[String] //todo: add issue number to title
              val text = (value \ "body").asOpt[String].getOrElse("")
              Some(IntegrationTopic(0, ID, Some(s"Issue/$id"), groupId, integrationToken.userId, userId, timestamp, title, title))
            } else None
          }
        case _ => Future(None)
      }
    }
  }
}
