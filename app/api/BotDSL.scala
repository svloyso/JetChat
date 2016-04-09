package api

import java.net.UnknownHostException
import actors.BotActor
import akka.actor._

/**
  * Created by dsavvinov on 4/7/16.
  */



case class TextMessage(senderId : Long, groupId : Long, topicId : Long, text : String)
case class BotInternalOutcomingMessage(adresseeId : Long, groupId : Long, topicId : Long, text: String)

abstract class DummyClass() {

    def apply() : ActorRef => Actor.Receive
}


class Talk(handler: ActorRef => Actor.Receive, parent : ActorRef) extends Actor {
    var currentState = "Initializing"

    def receive = handler(parent)
}

class Bot(system: ActorSystem, name: String, handler: ActorRef => Actor.Receive) extends BotActor(system, name) {
    private val usersToTalks: collection.mutable.Map[Long, ActorRef] = collection.mutable.Map()
    //TODO: подумать, действительно ли нам нужен абстрактный класс?

    override def receiveMsg(senderId : Long, groupId: Long, topicId: Long, text: String) : Unit = {
        usersToTalks.get(senderId) match {
            case Some(talk) => talk ! TextMessage(senderId, groupId, topicId, text)
            case None =>
                val newTalk = context.actorOf(Props(classOf[Talk], handler, self), s"bot-$id-talk-wth-$senderId")
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
