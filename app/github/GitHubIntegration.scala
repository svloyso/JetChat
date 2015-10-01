package github

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Singleton

import api._
import github.GitHubIntegration.GitHubMessageHandler
import models.{IntegrationTopic, IntegrationUpdate, AbstractMessage}
import play.api.Play
import play.api.http.{MimeTypes, HeaderNames}
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSResponse, WS}
import play.api.mvc.Results._
import play.api.mvc.{Result, AnyContent, Request}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author Alefas
 * @since  15/09/15
 */
@Singleton
class GitHubIntegration extends Integration {
  override def id: String = GitHubIntegration.ID

  override def name: String = GitHubIntegration.ID

  override def hookHandler: HookHandler = new HookHandler {
    override def init(token: String): Unit = {}

    override def handle(): Unit = {}
  }

  override def authentificator: Authentificator = new Authentificator {
    override def disable(): Unit = {
      //todo:
    }

    override def token(redirectUri: String, code: String): Future[String] = {
      val tokenResponse = WS.url("https://github.com/login/oauth/access_token")(Play.current).
        withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON).
        post(Map("client_id" -> Seq(GitHubIntegration.clientId),
          "client_secret" -> Seq(GitHubIntegration.clientSecret),
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
      Future.successful(Redirect(GitHubIntegration.getAuthorizationUrl(callbackUrl, scope, state)))
    }
  }

  override def messageHandler: MessageHandler = new GitHubMessageHandler
}

object GitHubIntegration {
  val ID = "GitHub"

  val sincePeriod = 1000 * 60 * 60 * 24 * 7

  //todo: split for localhost and server, currently it's just localhost
  private val clientId = "74d1dadc710087464a77"
  private val clientSecret = "b00475966e9c381c00b1616dd9fabb1d3d4b285d"

  private def getAuthorizationUrl(redirectUri: String, scope: String, state: String): String = {
    s"https://github.com/login/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&scope=$scope&state=$state"
  }

  class GitHubMessageHandler extends MessageHandler {
    override def collectMessages(token: String): Future[Map[IntegrationTopic, Seq[IntegrationUpdate]]] = {
      def askAPI(url: String): Future[WSResponse] = {
        WS.url(url)(Play.current).
          withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON).get()
      }

      val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
      val since = dateFormat.format(new Date(System.currentTimeMillis() - sincePeriod))
      val notificationsUrl = s"https://api.github.com/notifications?access_token=$token&all=true&since=$since"
      askAPI(notificationsUrl).flatMap { response =>
        val seq: Seq[JsValue] = response.json.as[Seq[JsValue]]
        Future.sequence(seq.map { value =>
          val groupId = (value \ "repository" \ "full_name").as[String]
          val subject = value \ "subject"
          val title = (subject \ "title").as[String]
          val tp = (subject \ "type").as[String]
          val url = (subject \ "url").as[String]
          val index = url.lastIndexOf('/')
          val lastPart = url.substring(index + 1)
          val topicId = tp match {
            case "Commit" => s"$title (${lastPart.substring(0, 7)})"
            case "Issue" => s"#$lastPart $title"
            case "PullRequest" => s"PR #$lastPart $title"
          }

          askAPI(url + s"?access_token=$token").flatMap { response =>
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
            val integrationTopic = IntegrationTopic(ID, topicId, groupId, topicAuthor, topicTimestamp, topicText)

            askAPI((json \ "comments_url").as[String]).map { response =>
              val json = response.json
              json.as[Seq[JsValue]].map { value =>
                //todo: status changes?
                val commentId = (value \ "id").as[Long]
                val userId = (value \ "user" \ "login").as[String]
                val timestamp = new Timestamp(dateFormat.parse((value \ "created_at").as[String]).getTime)
                val text = (value \ "body").as[String]
                IntegrationUpdate(ID, commentId.toString, groupId, topicId, userId, timestamp, text)
              }
            }.map(integrationTopic -> _)
          }
        }).map(_.toMap)
      }
    }

    override def sendMessage(messages: Seq[AbstractMessage]): Unit = {} //todo:
  }
}
