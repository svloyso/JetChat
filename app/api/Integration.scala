package api

import models.AbstractMessage

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
  def enable() //todo: Future?
  def disable() //todo: Future?
}

trait HookHandler {
  def handle() //todo: Future?
}

trait MessageHandler {
  def sendMessage(messages: Seq[AbstractMessage]) //todo: Future?
}
