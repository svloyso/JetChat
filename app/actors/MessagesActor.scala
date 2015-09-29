package actors

import java.util

import akka.actor.{Props, Actor}
import akka.actor.Actor.Receive
import api.Integration
import models.api.IntegrationTokensDAO

/**
 * @author Alefas
 * @since  18/09/15
 */
class MessagesActor(integrations: util.Set[Integration], integrationTokensDAO: IntegrationTokensDAO) extends Actor {
  override def receive: Receive = {
    case ReceiveMessagesEvent =>
      //todo:
  }
}

object MessagesActor {
  def props(integrations: util.Set[Integration], integrationTokensDAO: IntegrationTokensDAO): Props =
    Props(new MessagesActor(integrations, integrationTokensDAO))
}

case object ReceiveMessagesEvent
