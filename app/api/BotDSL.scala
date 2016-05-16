package api

import actors.BotActor
import akka.actor._
import models.User

/**
  * Created by dsavvinov on 4/7/16.
  */



case class TextMessage(senderId : Long, groupId : Long, topicId : Long, text : String)
case class BotInternalOutcomingMessage(adresseeId : Long, groupId : Long, topicId : Long, text: String)

abstract class DummyClass() {
    def apply() : ActorRef => Actor.Receive
}

class State()(handler : Function[Any, Any]) {
    def say() = {}
}

class Talk(handler: ActorRef => Actor.Receive, parent : ActorRef) extends Actor {
    var currentState = "Initializing"

    def receive = handler(parent)
}

class BotDSL(system: ActorSystem, user: User, handler: ActorRef => Actor.Receive) extends BotActor(system, user) {
    private val usersToTalks: collection.mutable.Map[Long, ActorRef] = collection.mutable.Map()
    //TODO: подумать, действительно ли нам нужен абстрактный класс?

    override def receiveMsg(senderId : Long, groupId: Long, topicId: Long, text: String) : Unit = {
        usersToTalks.get(senderId) match {
            case Some(talk) => talk ! TextMessage(senderId, groupId, topicId, text)
            case None =>
                val newTalk = context.actorOf(Props(classOf[Talk], handler, self), s"bot-${user.id}-talk-wth-$senderId")
                usersToTalks += senderId -> newTalk
                newTalk ! TextMessage(senderId, groupId, topicId, text)
        }
    }

    override def receiveOther(msg : Any) : Unit = {
        msg match {
            case BotInternalOutcomingMessage (senderId, groupId, topicId, text) =>
                send (groupId, topicId, text)
            case msg: Any =>
                scala.Console.println("Unhandled message")
        }
    }
}
