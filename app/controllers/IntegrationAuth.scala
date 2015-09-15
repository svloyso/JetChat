package controllers

import javax.inject.{Inject, Singleton}

import api.{Utils, Integration}
import play.api.mvc.Results._
import play.api.mvc.{Action, Controller}
import play.twirl.api.TemplateMagic.javaCollectionToScala

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * @author Alefas
 * @since  15/09/15
 */
@Singleton
class IntegrationAuth @Inject()(integrations: java.util.Set[Integration]) extends Controller {
  def auth(id: String, redirectUrl: Option[String]) = Action.async { implicit request =>
    integrations.toSeq.find(_.id == id) match {
      case Some(integration) => integration.authentificator.enable(redirectUrl)
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
          oauthState <- request.session.get(id + "-oauth-state")
        } yield {
            if (state == oauthState) {
              val callbackUrl = Utils.callbackUrl(id, redirectUrl)
              integration.authentificator.token(callbackUrl, code).flatMap { accessToken =>
                request.cookies.get("user") match {
                  case Some(cookie) =>
                    val id = cookie.value
                    //todo: DB action
                    Future.successful(Redirect(controllers.routes.Application.index(None, None, None)))
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
