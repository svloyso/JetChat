package controllers

import javax.inject.{Inject, Singleton}

import api.Integration
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future

/**
 * @author Alefas
 * @since  16/09/15
 */
@Singleton
class IntegrationHook @Inject()(integrations: java.util.Set[Integration]) extends Controller {
  def hook(id: String) = Action.async(parse.json) { implicit request =>
    Future.successful(BadRequest("NYI")) //todo: there is no integrations yet to use it
  }
}
