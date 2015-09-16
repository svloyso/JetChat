package api

import models.AbstractMessage
import play.api.mvc.{Result, Request, AnyContent}

import scala.concurrent.Future

/**
 * @author Alefas
 * @since  15/09/15
 */
trait Integration {
  def id: String
  def name: String
  def authentificator: Authentificator
  def hookHandler: HookHandler
  def messageHandler: MessageHandler
}

trait Authentificator {
  def enable(redirectUrl: Option[String], state: String)(implicit request: Request[AnyContent]): Future[Result]
  def disable() //todo: Future?
  def token(redirectUri: String, code: String): Future[String]
}

trait HookHandler {
  def handle() //todo: Future?
}

trait MessageHandler {
  def sendMessage(messages: Seq[AbstractMessage]) //todo: Future?
}
