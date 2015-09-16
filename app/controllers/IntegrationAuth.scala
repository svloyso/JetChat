package controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}

import akka.actor.FSM.Failure
import akka.actor.Status.Success
import api.{Utils, Integration}
import models.UsersDAO
import models.api.{IntegrationToken, IntegrationTokensDAO}
import play.api.mvc.Results._
import play.api.mvc.{Call, Action, Controller}
import play.twirl.api.TemplateMagic.javaCollectionToScala

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author Alefas
 * @since  15/09/15
 */
@Singleton
class IntegrationAuth @Inject()(integrations: java.util.Set[Integration],
                               integrationTokensDAO: IntegrationTokensDAO,
                               usersDAO: UsersDAO) extends Controller {
  def auth(id: String, redirectUrl: Option[String]) = Action.async { implicit request =>
    integrations.toSeq.find(_.id == id) match {
      case Some(integration) =>
        val state = UUID.randomUUID().toString
        integration.authentificator.enable(redirectUrl, state).map(_.withSession(s"${integration.id}-oauth-state" -> state))
      case None => Future.successful(BadRequest("Wrong service"))
    }
  }

  def callback(id: String, codeOpt: Option[String] = None, stateOpt: Option[String] = None,
               redirectUrl: Option[String] = None) = Action.async { implicit request =>
    integrations.find(_.id == id) match {
      case Some(integration) =>
        (for {
          code <- codeOpt
          state <- stateOpt
          oauthState <- request.session.get(s"$id-oauth-state")
        } yield {
            if (state == oauthState) {
              val callbackUrl = Utils.callbackUrl(id, redirectUrl)
              integration.authentificator.token(callbackUrl, code).flatMap { accessToken =>
                request.cookies.get("user") match {
                  case Some(cookie) =>
                    val login = cookie.value
                    (for {
                      user <- usersDAO.findByLogin(login)
                      result <- integrationTokensDAO.merge(IntegrationToken(user.get.id, id, accessToken))
                    } yield result).map { _ => Redirect(controllers.routes.Application.index(None, None, None))
                    }.recover { case e: Throwable => BadRequest(e.getMessage) }
                  case _ => Future.successful(BadRequest("User is logged off"))
                }
              }.recover {
                case ex: IllegalStateException => Unauthorized(ex.getMessage)
              }
            } else {
              Future.successful(BadRequest(s"Invalid $id login"))
            }
          }).getOrElse(Future.successful(BadRequest("No parameters supplied")))
      case None => Future.successful(BadRequest("Wrong service"))
    }
  }
}
