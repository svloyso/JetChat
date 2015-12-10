package actors

import _root_.api.{CollectedMessages, Integration}
import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import models._
import models.api.IntegrationTokensDAO
import play.api.Logger
import play.api.libs.json.JsObject

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
 * @author Alefas
 * @since  18/09/15
 */
class MessagesActor(integration: Integration, system: ActorSystem,
                    integrationTokensDAO: IntegrationTokensDAO,
                    integrationTopicsDAO: IntegrationTopicsDAO,
                    integrationUpdatesDAO: IntegrationUpdatesDAO,
                    integrationUsersDAO: IntegrationUsersDAO,
                    integrationGroupsDAO: IntegrationGroupsDAO) extends Actor {
  val mediator = DistributedPubSub(system).mediator

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
          MessagesActor.LOG.debug(s"${integration.id} messages was collected. Topic updates: ${messages.size}.")
          for ((topic, updates) <- messages) {
            //todo: proper user
            integrationUsersDAO.merge(IntegrationUser(integration.id, None, topic.integrationUserId, "John Doe", None)).onSuccess {
              case true =>
                mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification
            }
            //todo: proper group id
            integrationGroupsDAO.merge(IntegrationGroup(integration.id, topic.integrationGroupId, "Some name")).onSuccess {
              case true =>
                mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification
            }
            integrationTopicsDAO.merge(topic).onSuccess {
              case true =>
                mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification
            }
            updates.foreach { update =>
              //todo: proper user
              integrationUsersDAO.merge(IntegrationUser(integration.id, None, update.integrationUserId, "John Doe", None)).onSuccess {
                case true =>
                  mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification
              }
              //todo: proper group id
              integrationGroupsDAO.merge(IntegrationGroup(integration.id, update.integrationGroupId, "Some name")).onSuccess {
                case true =>
                  mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification
              }
              integrationUpdatesDAO.merge(update).onSuccess {
                case true =>
                  mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification
              }}
          }
          duration.max(nextCheck)
        })
      }.onFailure { case _ => schedule(MessagesActor.DEFAULT_DURATION) }
  }
}

object MessagesActor {
  val DEFAULT_DURATION = 30.seconds
  val LOG = Logger.apply(this.getClass)

  def actorOf(integration: Integration, system: ActorSystem,
              integrationTokensDAO: IntegrationTokensDAO,
              integrationTopicsDAO: IntegrationTopicsDAO,
              integrationUpdatesDAO: IntegrationUpdatesDAO,
              integrationUsersDAO: IntegrationUsersDAO,
              integrationGroupsDAO: IntegrationGroupsDAO): ActorRef =
    system.actorOf(Props(new MessagesActor(integration, system, integrationTokensDAO,
      integrationTopicsDAO, integrationUpdatesDAO, integrationUsersDAO, integrationGroupsDAO)),
      s"messages-actor:${ActorUtils.encodePath(integration.id)}")

  def actorSelection(integration: Integration, system: ActorSystem): ActorSelection =
    system.actorSelection(s"/user/messages-actor:${ActorUtils.encodePath(integration.id)}")
}

case object ReceiveMessagesEvent