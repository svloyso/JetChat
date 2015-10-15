package api

import models.{IntegrationTopic, IntegrationUpdate, AbstractMessage}
import play.api.mvc.{Result, Request, AnyContent}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * @author Alefas
 * @since  15/09/15
 */
trait Integration {
  def id: String
  def name: String
  def authentificator: Authentificator
  def hookHandler: HookHandler //todo: make optional
  def messageHandler: MessageHandler
}

trait Authentificator {
  def enable(redirectUrl: Option[String], state: String)(implicit request: Request[AnyContent]): Future[Result]
  def disable(token: String): Future[Boolean]
  def token(redirectUri: String, code: String): Future[String]
}

trait HookHandler {
  def init(token: String): Unit
  def handle() //todo:
}

trait MessageHandler {
  def collectMessages(token: String): Future[CollectedMessages]
  def sendMessage(messages: Seq[AbstractMessage]) //todo: Future?
}

case class CollectedMessages(messages: Map[IntegrationTopic, Seq[IntegrationUpdate]], nextCheck: FiniteDuration)