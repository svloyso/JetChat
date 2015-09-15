package github

import java.util.UUID
import javax.inject.Qualifier

import api.{HookHandler, Authentificator, MessageHandler, Integration}
import models.AbstractMessage
import play.api.mvc.Results._
import play.api.mvc.{Result, AnyContent, Request}

import scala.concurrent.Future

/**
 * @author Alefas
 * @since  15/09/15
 */
@Qualifier
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

    override def enable(redirectUrl: Option[String])(implicit request: Request[AnyContent]): Future[Result] = {
      val callbackUrl = controllers.routes.IntegrationAuth.callback(id, None, None, redirectUrl).absoluteURL()
      val scope = "user, repo, gist, read:org"
      val state = UUID.randomUUID().toString
      Future.successful(Redirect(GitHubIntegration.getAuthorizationUrl(callbackUrl, scope, state)).
        withSession("github-oauth-state" -> state))
    }
  }

  override def messageHandler: MessageHandler = new MessageHandler {
    override def sendMessage(messages: Seq[AbstractMessage]): Unit = {
      //todo:
    }
  }
}

object GitHubIntegration {
  private val clientId = "id" //todo:
  private val clientSecret = "secret" //todo:

  private def getAuthorizationUrl(redirectUri: String, scope: String, state: String): String = {
    s"https://github.com/login/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&scope=$scope&state=$state"
  }
}
