package api

import models.api.IntegrationToken
import models.{IntegrationTopic, IntegrationUpdate}
import play.api.Play
import play.api.mvc.{AnyContent, Request, Result}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * @author Alefas
 * @since  15/09/15
 */
trait Integration {
  def id: String
  def name: String
  def authentificator: OAuthAuthentificator
  def hookHandler: Option[HookHandler]
  def messageHandler: MessageHandler
  def userHandler: UserHandler
}

trait OAuthAuthentificator {
  def integrationId: String
  final def clientId: String = Play.current.configuration.getString(s"api.$integrationId.clientId").get
  final def clientSecret: String = Play.current.configuration.getString(s"api.$integrationId.clientSecret").get
  def auth(redirectUrl: Option[String], state: String)(implicit request: Request[AnyContent]): Future[Result]
  def logout(token: String): Future[Boolean]
  def token(redirectUri: String, code: String): Future[String]
}

trait HookHandler {
  def init(token: String): Unit
  def handle() //todo:
}

trait MessageHandler {
  def collectMessages(integrationToken: IntegrationToken): Future[CollectedMessages]
  def sendMessage(integrationToken: IntegrationToken, groupId: String, topicId: String, message: SentMessage, messageId: Long): Future[Option[IntegrationUpdate]]
}

trait UserHandler {
  def login(token: String): Future[String]
  def name(token: String, login: Option[String] = None): Future[Option[String]]
  def avatarUrl(token: String, login: Option[String] = None): Future[Option[String]]
  def groupName(token: String, groupId: String): Future[String]
}

case class CollectedMessages(messages: Map[IntegrationTopic, Seq[IntegrationUpdate]], nextCheck: FiniteDuration)

sealed trait SentMessage
case class TopicComment(text: String) extends SentMessage