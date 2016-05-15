package bots.dsl.backend

import actors.BotActor
import akka.actor.{Props, ActorRef, ActorSystem}
import bots.dsl.backend.BotMessages._
import bots.dsl.frontend._

import scala.concurrent.duration.FiniteDuration

/**
  * Created by dsavvinov on 5/13/16.
  */
/** actual bot actor class **/
class BotActorImplementation(
                              system:           ActorSystem,
                              name:             String,
                              statesToHandlers: collection.mutable.Map[String, Behaviour],
                              startingState:    String,
                              talkStorage:      BotDataStorage,
                              globalBehaviour:  GlobalBehaviour
                            ) extends BotActor(system, name) {

  globalBehaviour.bindToBotActor(this)

  private val usersToTalks = collection.mutable.Map[Long, ActorRef]()
  private val chatRooms = collection.mutable.Set[(Long, Long)]()

  override def receiveMsg(senderId: Long, groupId: Long, topicId: Long, text: String) = {
    chatRooms += ( (groupId, topicId) )
    usersToTalks.get(senderId) match {
      case Some(talk) => talk ! TextMessage(senderId, groupId, topicId, text)
      case None =>
        globalBehaviour.talkCreatedProcessor(senderId)

        val localTalkHandlers = statesToHandlers.clone()
        val localTalkStorage  = talkStorage.clone()
        val newTalk           = context.actorOf(
          Props(classOf[Talk], senderId, groupId, topicId, localTalkHandlers, self, startingState, localTalkStorage),
          s"bot-$id-talk-wth-$senderId"
        )
        usersToTalks += senderId -> newTalk
        newTalk ! TextMessage(senderId, groupId, topicId, text)
    }
  }

  override def receiveOther(msg: Any): Unit = {
    msg match {
      case SendToUser(senderId, groupId, topicId, text) =>
        send(groupId, topicId, text)
      case BroadcastMessage(text) =>
        chatRooms foreach { case (groupId, topicId) => send(groupId, topicId, text) }
      case ScheduledTask(task) =>
        task()
      case maybeUserDefinedMessage: Any =>
        val handler = globalBehaviour.messageReceiveProcessor
        if (handler.isDefinedAt(maybeUserDefinedMessage)) {
          handler(maybeUserDefinedMessage)
        }
        else {
          throw new InternalError("Error: unhandled message " ++ msg.toString)
        }
    }
  }

  def sendTo(userID: Long, text: String) = {
    sendDirect(userID, text)
  }

  def schedule(task: (Unit => Any), duration: FiniteDuration): Unit = {
    import context._
    context.system.scheduler.scheduleOnce(duration, context.self, new ScheduledTask(task))
  }
}
