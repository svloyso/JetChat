package actors

import java.util.concurrent.TimeoutException

import _root_.api.{CollectedMessages, Integration, TopicComment}
import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import models._
import models.api.IntegrationTokensDAO
import play.api.Logger
import play.api.libs.json.JsObject

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Failure, Success}

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
    case SendMessageEvent(userId, integrationGroupId, integrationTopicId, text, messageId) =>
      val integrationId = integration.id
      try {
        Await.result(for {
          tokenOption <- integrationTokensDAO.find(userId, integrationId)
          if tokenOption.isDefined
          token = tokenOption.get
          realUpdate <- integration.messageHandler.sendMessage(token, integrationGroupId, integrationTopicId, TopicComment(text), messageId)
          if realUpdate.isDefined
          result <- integrationUpdatesDAO.merge(realUpdate.get)
        } yield mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))), //todo: add proper notification
          30.seconds)
      } catch {
        case t: TimeoutException => //todo: proper handling of such cases
      }
    case ReceiveMessagesEvent =>
      def schedule(duration: FiniteDuration): Unit = {
        system.scheduler.scheduleOnce(duration, new Runnable {
          override def run(): Unit = {
            MessagesActor.actorSelection(integration, system) ! ReceiveMessagesEvent
          }
        })
      }
      try {
        Await.result(integrationTokensDAO.allTokens(integration.id).flatMap { tokens =>
          Future.sequence(tokens.map { integrationToken =>
            val token = integrationToken.token
            integration.messageHandler.collectMessages(integrationToken).map {
              case CollectedMessages(messages, nextCheck) =>
                MessagesActor.LOG.debug(s"${integration.id} messages was collected. Topic updates: ${messages.size}.")
                for ((topic, updates) <- messages) {
                  val topicLogin = topic.integrationUserId
                  (for {
                    name <- integration.userHandler.name(token, Some(topicLogin))
                    avatar <- integration.userHandler.avatarUrl(token, Some(topicLogin))
                    result <- integrationUsersDAO.merge(IntegrationUser(integration.id, None, topicLogin, name.getOrElse(topicLogin), avatar))
                  } yield result).onSuccess { case success =>
                    if (success)
                      mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification

                    (for {
                      groupName <- integration.userHandler.groupName(token, topic.integrationGroupId)
                      result <- integrationGroupsDAO.merge(IntegrationGroup(integration.id, topic.integrationGroupId, integrationToken.userId, groupName))
                    } yield result).onSuccess { case success =>
                      if (success)
                        mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification

                      integrationTopicsDAO.merge(topic).onSuccess { case success =>
                        if (success)
                          mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification

                        updates.foreach { update =>
                          val updateLogin = update.integrationUserId
                          (for {
                            name <- integration.userHandler.name(token, Some(updateLogin))
                            avatar <- integration.userHandler.avatarUrl(token, Some(updateLogin))
                            result <- integrationUsersDAO.merge(IntegrationUser(integration.id, None, updateLogin, name.getOrElse(updateLogin), avatar))
                          } yield result).onSuccess { case success =>
                            if (success)
                              mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification

                              (for {
                                groupName <- integration.userHandler.groupName(token, update.integrationGroupId)
                                result <- integrationGroupsDAO.merge(IntegrationGroup(integration.id, update.integrationGroupId, integrationToken.userId, groupName))
                              } yield result).onSuccess { case success =>
                                if (success)
                                  mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification

                                  // TODO: We need to check if the update has been already inserted
                                  integrationUpdatesDAO.merge(update).onComplete {
                                    case Success(inserted) =>
                                      mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification
                                    case Failure(e) =>
                                      MessagesActor.LOG.error(s"Can't merge an integration update: $update", e)
                                  }
                              }
                          }
                        }
                      }
                    }
                  }
                }
                nextCheck
            }.recover {
              case t =>
                MessagesActor.LOG.error(t.getMessage, t)
                MessagesActor.DEFAULT_DURATION
            }
          })
        }.map { durations =>
          schedule((durations :+ MessagesActor.DEFAULT_DURATION).max)
        }.recover { case _ => schedule(MessagesActor.DEFAULT_DURATION) },
          5.minutes
        )
      } catch {
        case t: TimeoutException => schedule(MessagesActor.DEFAULT_DURATION)
      }
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

case class SendMessageEvent(userId: Long, integrationGroupId: String, integrationTopicId: String, text: String, messageId: Long)