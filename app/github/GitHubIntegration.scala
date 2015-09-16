package github

import java.util.UUID
import javax.inject.{Singleton, Qualifier}

import api._
import models.AbstractMessage
import play.api.Play
import play.api.http.{MimeTypes, HeaderNames}
import play.api.libs.ws.WS
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
  override def id: String = "GitHub"

  override def name: String = "GitHub"

  override def hookHandler: HookHandler = new HookHandler {
    override def handle(): Unit = {
      //todo:
    }
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

  override def messageHandler: MessageHandler = new MessageHandler {
    override def sendMessage(messages: Seq[AbstractMessage]): Unit = {
      //todo:
    }
  }
}

object GitHubIntegration {
  //todo: split for localhost and server
  private val clientId = "74d1dadc710087464a77"
  private val clientSecret = "b00475966e9c381c00b1616dd9fabb1d3d4b285d"

  private def getAuthorizationUrl(redirectUri: String, scope: String, state: String): String = {
    s"https://github.com/login/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&scope=$scope&state=$state"
  }
}
