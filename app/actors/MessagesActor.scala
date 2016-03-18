package actors

import _root_.api.{CollectedMessages, Integration, TopicComment}
import actors.ActorUtils.FutureExtensions
import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import models._
import models.api.IntegrationTokensDAO
import play.api.libs.json.JsObject

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Failure, Success}

/**
 * @author Alefas
 * @since  18/09/15
 */
class MessagesActor(integration: Integration,
                    integrationTokensDAO: IntegrationTokensDAO,
                    integrationTopicsDAO: IntegrationTopicsDAO,
                    integrationUpdatesDAO: IntegrationUpdatesDAO,
                    integrationUsersDAO: IntegrationUsersDAO,
                    integrationGroupsDAO: IntegrationGroupsDAO)
                   (implicit system: ActorSystem) extends MasterActor with ActorLogging {
  val mediator = DistributedPubSub(system).mediator

  //If events contains value, so this user will get receive events, otherwise start schedule
  val events: mutable.HashMap[Long, mutable.Queue[Any]] = mutable.HashMap.empty
  val evaluating: mutable.HashMap[Long, Boolean] = mutable.HashMap.empty

  val sinceMap: mutable.HashMap[Long, Long] = mutable.HashMap.empty

  def schedule(duration: FiniteDuration, message: Any): Unit = {
    system.scheduler.scheduleOnce(duration, new Runnable {
      override def run(): Unit = {
        MessagesActor.actorSelection(integration, system) ! message
      }
    })
  }

  private def nextEvent(event: Any): Unit = {
    event match {
      case SendMessage(userId, integrationGroupId, integrationTopicId, text, messageId) =>
        evaluating.update(userId, true)

        log.info(s"Sending messages: { userId: $userId, integrationGroupId: $integrationGroupId, integrationTopicId: $integrationTopicId }")

        val integrationId = integration.id
        implicit val timeout = 1.second

        (for {
          tokenOption <- integrationTokensDAO.find(userId, integrationId)
          if tokenOption.isDefined
          token = tokenOption.get
          if token.enabled
          realUpdate <- integration.messageHandler.sendMessage(token, integrationGroupId, integrationTopicId, TopicComment(text), messageId)
          if realUpdate.isDefined
          result <- integrationUpdatesDAO.merge(realUpdate.get)
        } yield {
          mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification
        }).withTimeout.onComplete { t =>
          t match {
            case Failure(throwable) => log.error(throwable, throwable.getMessage)
            case _ =>
          }
          self ! FinishTask(userId)
        }
      case ReceiveMessages(userId) =>
        implicit val timeout = 5.minutes

        log.info(s"Receiving messages: { userId: $userId, integrationId: ${integration.id} }")

        integrationTokensDAO.find(userId, integration.id).flatMap {
          case integrationTokenOption =>
            integrationTokenOption match {
              case Some(integrationToken) if integrationToken.enabled =>
                val token = integrationToken.token

                val names: mutable.HashMap[String, Future[Option[String]]] = mutable.HashMap.empty
                val avatars: mutable.HashMap[String, Future[Option[String]]] = mutable.HashMap.empty
                def name(topicLogin: String): Future[Option[String]] = {
                  names.getOrElseUpdate(topicLogin, integration.userHandler.name(token, Some(topicLogin)))
                }
                def avatar(topicLogin: String): Future[Option[String]] = {
                  avatars.getOrElseUpdate(topicLogin, integration.userHandler.avatarUrl(token, Some(topicLogin)))
                }

                integration.messageHandler.collectMessages(integrationToken, sinceMap.get(userId)).map {
                  case CollectedMessages(messages, nextCheck, newSince) =>
                    log.info(s"${integration.id} messages was collected. Topic updates: ${messages.size}.")
                    for ((topic, updates) <- messages) {
                      val topicLogin = topic.integrationUserId
                      (for {
                        name <- name(topicLogin)
                        avatar <- avatar(topicLogin)
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
                                name <- name(updateLogin)
                                avatar <- avatar(updateLogin)
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
                                      log.error(s"Can't merge an integration update: $update", e)
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }

                    val oldSince = sinceMap.getOrElse(userId, 0L)
                    sinceMap.update(userId, newSince.max(oldSince))
                    nextCheck
                }.recover {
                  case throwable: Throwable =>
                    log.error(throwable.getMessage, throwable)
                    MessagesActor.DEFAULT_DURATION
                }.map { duration =>
                  schedule(duration, ReceiveMessages(userId))
                }
              case _ => Future { self ! FinishSchedule(userId) }
            }
        }.withTimeout.onComplete { t =>
          t match {
            case Failure(throwable) => log.error(throwable, throwable.getMessage)
            case _ =>
          }
          self ! FinishTask(userId)
        }
    }
  }

  override def receiveAsMaster: Receive = {
    case FinishTask(userId) =>
      evaluating.update(userId, false)
      self ! NextTask(userId)
    case NextTask(userId) =>
      if (!evaluating.getOrElseUpdate(userId, false)) {
        events.get(userId) match {
          case Some(queue) if queue.nonEmpty => nextEvent(queue.dequeue())
          case _ =>
        }
      }
    case StartSchedule(userId) =>
      if (!events.contains(userId)) {
        events.update(userId, mutable.Queue.empty)
        self ! ReceiveMessages(userId)
      }
    case FinishSchedule(userId) =>
      if (events.contains(userId)) {
        events.remove(userId)
      }
    case event@ReceiveMessages(userId) =>
      events.getOrElseUpdate(userId, mutable.Queue.empty).enqueue(event)
      self ! NextTask(userId)
    case event@SendMessage(userId, _, _, _, _) =>
      if (!events.contains(userId)) {
        events.getOrElseUpdate(userId, mutable.Queue.empty).enqueue(ReceiveMessages(userId)) // start schedule
      }
      events.getOrElseUpdate(userId, mutable.Queue.empty).enqueue(event)
      self ! NextTask(userId)
    case CollectReceivers =>
      log.info(s"Receiving messages: ${integration.id}")

      integrationTokensDAO.allTokens(integration.id).map { tokens =>
        tokens.filter(_.enabled).foreach { integrationToken =>
          self ! StartSchedule(integrationToken.userId)
        }
        schedule(MessagesActor.DEFAULT_DURATION, CollectReceivers)
      }.recover { case _ => schedule(MessagesActor.DEFAULT_DURATION, CollectReceivers) }
  }

  override def turningMaster(): Unit = {
    self ! CollectReceivers
    super.turningMaster()
  }
}

object MessagesActor {
  val DEFAULT_DURATION = 30.seconds

  def actorOf(integration: Integration, system: ActorSystem,
              integrationTokensDAO: IntegrationTokensDAO,
              integrationTopicsDAO: IntegrationTopicsDAO,
              integrationUpdatesDAO: IntegrationUpdatesDAO,
              integrationUsersDAO: IntegrationUsersDAO,
              integrationGroupsDAO: IntegrationGroupsDAO): ActorRef =
    system.actorOf(Props(new MessagesActor(integration, integrationTokensDAO,
      integrationTopicsDAO, integrationUpdatesDAO, integrationUsersDAO, integrationGroupsDAO)(system)),
      s"messages-actor:${ActorUtils.encodePath(integration.id)}")

  def actorSelection(integration: Integration, system: ActorSystem): ActorSelection =
    system.actorSelection(s"/user/messages-actor:${ActorUtils.encodePath(integration.id)}")
}

case object CollectReceivers
case class FinishTask(userId: Long)
case class NextTask(userId: Long)
case class ReceiveMessages(userId: Long)
case class SendMessage(userId: Long, integrationGroupId: String, integrationTopicId: String, text: String, messageId: Long)
case class StartSchedule(userId: Long)
case class FinishSchedule(userId: Long)