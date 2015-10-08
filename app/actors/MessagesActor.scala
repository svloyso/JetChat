package actors

import akka.actor._
import api.{CollectedMessages, Integration}
import models.api.IntegrationTokensDAO

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
 * @author Alefas
 * @since  18/09/15
 */
class MessagesActor(integration: Integration, system: ActorSystem, integrationTokensDAO: IntegrationTokensDAO) extends Actor {
  override def receive: Receive = {
    case ReceiveMessagesEvent =>
      def schedule(duration: FiniteDuration): Unit = {
        system.scheduler.scheduleOnce(duration, new Runnable {
          override def run(): Unit = {
            MessagesActor.actorSelection(integration, system) ! ReceiveMessagesEvent
          }
        })
      }

      integrationTokensDAO.allTokens(integration.id).flatMap { tokens =>
        Future.sequence(tokens.map { token =>
          integration.messageHandler.collectMessages(token.token)
        })
      }.map { coll =>
        schedule(coll.foldLeft(MessagesActor.DEFAULT_DURATION) { case (duration, CollectedMessages(messages, nextCheck)) =>
          println(s"messages was collected ${System.currentTimeMillis() / 1000}")
          //todo: put to DB
          duration.max(nextCheck)
        })
      }.onFailure { case _ => schedule(MessagesActor.DEFAULT_DURATION) }
  }
}

object MessagesActor {
  val DEFAULT_DURATION = 30 seconds

  def actorOf(integration: Integration, system: ActorSystem, integrationTokensDAO: IntegrationTokensDAO): ActorRef =
    system.actorOf(Props(new MessagesActor(integration, system, integrationTokensDAO)), s"messages-actor:${integration.id}")

  def actorSelection(integration: Integration, system: ActorSystem): ActorSelection =
    system.actorSelection(s"/user/messages-actor:${integration.id}")
}

case object ReceiveMessagesEvent