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

  override def receiveMsg(msg: ChatMessage): Unit = {
    val group = msg.address.chatRoom.group
    val topic = msg.address.chatRoom.topic
    val sender = msg.address.user

    chatRooms += ( (group, topic) )
    usersToTalks.get(sender) match {
      case Some(talk) =>
        log.info(s"Redirecting message to talk $talk")
        talk ! msg
      case None =>
        log.info(s"""Creating new talk "bot-$id-talk-wth-${sender.id}"""")
        globalBehaviour.talkCreatedProcessor(sender)
        val localTalkHandlers = statesToHandlers.clone()
        val localTalkStorage  = talkStorage.clone()
        val newTalk           = context.actorOf(
          Props(classOf[Talk], sender, msg.address.chatRoom, localTalkHandlers, self, startingState, localTalkStorage),
          s"bot-$id-talk-wth-${sender.id}"
        )
        usersToTalks += sender.id -> newTalk
        receiveMsg(msg)
    }
  }

  override def receiveOther(msg: Any): Unit = {
    msg match {
      case SendToUser(address, text) =>
        log.info (s"""Got outcoming message "${text}" from child-talk""")
        send(address.chatRoom.group, address.chatRoom.topic, text)
      case BroadcastMessage(text) =>
        log.info (s"""Got broadcast request. Message: "$text" """)
        chatRooms foreach { case (groupId, topicId) => send(groupId, topicId, text) }
      case ScheduledTask(task) =>
        log.info (s"""Executing scheduled task""")
        task()
      case userDefinedMessage: UserMessage =>
        val handler = globalBehaviour.messageReceiveProcessor
          log.info (s"""Passing user-defined message $userDefinedMessage to user-handler""")
          handler(userDefinedMessage)
      case other =>
          throw new InternalError("Error: unhandled message " ++ msg.toString)
    }
  }

  def sendTo(user: User, text: String) = {
    sendDirect(user.id, text)
  }

  def schedule(task: (Unit => Any), duration: FiniteDuration): Unit = {
    import context._
    context.system.scheduler.scheduleOnce(duration, context.self, new ScheduledTask(task))
  }
}
