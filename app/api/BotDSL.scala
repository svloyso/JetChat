import java.net.UnknownHostException

import actors.BotActor
import akka.actor._

/**
  * Created by dsavvinov on 4/7/16.
  */


case class BotInternalIncomingMessage(senderId : Long, groupId : Long, topicId : Long, text : String)
case class BotInternalOutcomingMessage(adresseeId : Long, groupId : Long, topicId : Long, text: String)

class Talk extends Actor {
    def receive = {
        case BotInternalIncomingMessage(senderId, groupId, topicId, text) =>
             sender() ! BotInternalOutcomingMessage(senderId, groupId, topicId,
                 s"Recieved message from $senderId in group $groupId and topic $topicId " + "\n" +
                     s"Message: $text")
    }
}

class Bot(system: ActorSystem, name: String) extends BotActor(system, name) {
    private val usersToTalks: collection.mutable.Map[Long, ActorRef] = collection.mutable.Map()
    //TODO: подумать, действительно ли нам нужен абстрактный класс?

    override def botReceive(senderId : Long, groupId: Long, topicId: Long, text: String) : Unit = {
        usersToTalks.get(senderId) match {
            case Some(talk) => talk ! BotInternalIncomingMessage(senderId, groupId, topicId, text)
            case None =>
                val newTalk = context.actorOf(Props[Talk], s"bot-$id-talk-wth-$senderId")
                usersToTalks += senderId -> newTalk
                newTalk ! BotInternalIncomingMessage(senderId, groupId, topicId, text)
        }
    }

    def receiveOther : PartialFunction[Any, Unit] = {
        case BotInternalOutcomingMessage(senderId, groupId, topicId, text) =>
           send(groupId, topicId, text)
        case msg : Any =>
            throw new UnknownHostException()
    }
}
