package bots.dsl.backend

import actors.BotActor
import akka.actor.{Props, ActorRef, ActorSystem}
import bots.dsl.backend.BotMessages._
import bots.dsl.frontend._

/**
  * Created by dsavvinov on 5/13/16.
  */
/** actual bot actor class **/
class BotActorImplementation(
                              system: ActorSystem,
                              name: String,
                              statesToHandlers: collection.mutable.Map[String, Behaviour],
                              startingState: String,
                              talkStorage: BotDataStorage
                            ) extends BotActor(system, name) {

  private val usersToTalks: collection.mutable.Map[Long, ActorRef] = collection.mutable.Map()

  override def receiveMsg(senderId: Long, groupId: Long, topicId: Long, text: String): Unit = {
    usersToTalks.get(senderId) match {
      case Some(talk) => talk ! TextMessage(senderId, groupId, topicId, text)
      case None =>
        val localTalkHandlers = statesToHandlers.clone()
        val localTalkStorage = talkStorage.clone()
        val newTalk = context.actorOf(
          Props(classOf[Talk], senderId, groupId, topicId, localTalkHandlers, self, startingState, localTalkStorage),
          s"bot-$id-talk-wth-$senderId")
        usersToTalks += senderId -> newTalk
        newTalk ! TextMessage(senderId, groupId, topicId, text)
    }
  }

  override def receiveOther(msg: Any): Unit = {
    msg match {
      case BotInternalOutcomingMessage(senderId, groupId, topicId, text) =>
        send(groupId, topicId, text)
      case msg: Any =>
        throw new InternalError("Error: unhandled message " ++ msg.toString)
    }
  }
}
