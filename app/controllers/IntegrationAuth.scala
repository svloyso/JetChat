package controllers

import javax.inject.{Inject, Singleton}

import api.Integration
import play.api.mvc.{Action, Controller}
import play.twirl.api.TemplateMagic.javaCollectionToScala

import scala.concurrent.Future

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
    Future.successful(BadRequest("NYI")) //todo:
  }
}
