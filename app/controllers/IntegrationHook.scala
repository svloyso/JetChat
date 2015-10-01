package controllers

import javax.inject.{Inject, Singleton}

import api.Integration
import github.GitHubIntegration
import github.GitHubIntegration.GitHubMessageHandler
import models.UsersDAO
import models.api.IntegrationTokensDAO
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

/**
 * @author Alefas
 * @since  16/09/15
 */
@Singleton
class IntegrationHook @Inject()(integrations: java.util.Set[Integration], integrationTokensDAO: IntegrationTokensDAO,
                                usersDAO: UsersDAO) extends Controller {
  def hook(id: String) = Action.async(parse.json) { implicit request =>
    Future.successful(BadRequest("NYI")) //todo: there is no integrations yet to use it
  }

  //todo: to remove
  def check() = Action.async {
    val handler = new GitHubMessageHandler()
    for {
      users <- usersDAO.all
      user <- users
      integration <- integrationTokensDAO.find(user.id, "GitHub")
    } {
      handler.collectMessages(integration.get.token).onComplete { t =>
        t
      }
    }
    Future.successful(Ok("It's ok"))
  }
}
