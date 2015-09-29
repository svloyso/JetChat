package controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}

import actors.IntegrationEnabled
import akka.actor.ActorSystem
import akka.actor.FSM.Failure
import akka.actor.Status.Success
import api.{Utils, Integration}
import models.UsersDAO
import models.api.{IntegrationToken, IntegrationTokensDAO}
import play.api.mvc.Results._
import play.api.mvc.{Call, Action, Controller}
import scala.collection.JavaConversions._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author Alefas
 * @since  15/09/15
 */
@Singleton
class IntegrationAuth @Inject()(integrations: java.util.Set[Integration],
                               integrationTokensDAO: IntegrationTokensDAO,
                               usersDAO: UsersDAO,
                               system: ActorSystem) extends Controller {
  def auth(id: String, redirectUrl: Option[String]) = Action.async { implicit request =>
    integrations.toSeq.find(_.id == id) match {
      case Some(integration) =>
        val state = UUID.randomUUID().toString
        integration.authentificator.enable(redirectUrl, state).map(_.withSession(s"${integration.id}-oauth-state" -> state))
      case None => Future.successful(BadRequest("Wrong service"))
    }
  }

  def callback(integrationId: String, codeOpt: Option[String] = None, stateOpt: Option[String] = None,
               redirectUrl: Option[String] = None) = Action.async { implicit request =>
    integrations.find(_.id == integrationId) match {
      case Some(integration) =>
        (for {
          code <- codeOpt
          state <- stateOpt
          oauthState <- request.session.get(s"$integrationId-oauth-state")
        } yield {
            if (state == oauthState) {
              val callbackUrl = Utils.callbackUrl(integrationId, redirectUrl)
              integration.authentificator.token(callbackUrl, code).flatMap { accessToken =>
                request.cookies.get("user") match {
                  case Some(cookie) =>
                    val login = cookie.value
                    (for {
                      user <- usersDAO.findByLogin(login).map(_.get)
                      userId = user.id
                      result <- integrationTokensDAO.merge(IntegrationToken(userId, integrationId, accessToken))
                    } yield {
                      system.actorSelection("/user/integration-actor") ! IntegrationEnabled(userId, integrationId)
                      result
                    }).map { _ =>
                      Redirect(controllers.routes.Application.index(None, None, None))
                    }.recover { case e: Throwable => BadRequest(e.getMessage) }
                  case _ => Future.successful(BadRequest("User is logged off"))
                }
              }.recover {
                case ex: IllegalStateException => Unauthorized(ex.getMessage)
              }
            } else {
              Future.successful(BadRequest(s"Invalid $integrationId login"))
            }
          }).getOrElse(Future.successful(BadRequest("No parameters supplied")))
      case None => Future.successful(BadRequest("Wrong service"))
    }
  }
}
