package api

import actors.BotActor
import akka.actor._
import scala.language.dynamics

/**
  * Created by dsavvinov on 4/7/16.
  */


//TODO: is this hierarchy good?
trait Message

trait InternalMessage extends Message

trait ChatMessage extends Message

case class TextMessage(senderId: Long, groupId: Long, topicId: Long, text: String) extends ChatMessage

case class BotInternalOutcomingMessage(adresseeId: Long, groupId: Long, topicId: Long, text: String) extends InternalMessage

class DynamicProxyStorage extends Dynamic {
    var talk : Talk = null
    def selectDynamic(name: String) : Any = {
        talk.data.getHolder(name).dataValue
    }

    def updateDynamic(name: String)(value: Any) : Unit = {
        val holder = talk.data.getHolder(name)
        val newHolder = new DataHolder {
            override type DataType = holder.DataType
            override val dataValue: DataType = holder.dataValue
        }
        talk.data.dataStorage(name) = newHolder
    }


}
/** collection of proxy objects for unbound-DSL calls **/
trait Behaviour{
    var talk: Talk = null
    val data: DynamicProxyStorage = new DynamicProxyStorage()

    def handler(msg: TextMessage): Unit

    def bindToTalk(t: Talk) = {
        talk = t
        data.talk = t
    }

    /** redirecting calls to Talk **/
    def say(text: String): Unit = {
        talk.say(text)
    }

    def moveTo(newState: String): Unit = {
        talk.moveTo(newState)
    }
}

abstract class AbstractData() {

}

object State {
    def apply(stateName: String)(stateBehaviour: Behaviour): State = {
        new State(stateName)(stateBehaviour)
    }
}

class State(val stateName: String)(val stateBehaviour: Behaviour) {}

class Talk(
              val userId: Long,
              val groupId: Long,
              val topicId: Long,
              val statesToHandlers: collection.mutable.Map[String, Behaviour],
              val parent: ActorRef,
              var currentState: String,
              val data: BotDataStorage
          )
    extends Actor {


    /** internal methods and fields **/

    /** bind handlers to this talk **/
    statesToHandlers.foreach { case (s, b) => b.bindToTalk(this) }

    def receive = {
        case textMessage: TextMessage =>
            println(s"in state $currentState processing message ${textMessage.text}")
            statesToHandlers.get(currentState) match {
                case Some(behaviour) =>
                    behaviour.handler(textMessage)
                case None => throw new IllegalStateException(s"In talk <$this> incorrect state <$currentState> encountered")
            }
    }


    /** DSL-related methods **/
    def say(text: String) = {
        parent ! BotInternalOutcomingMessage(userId, groupId, topicId, text)
    }

    def moveTo(newState: String) = {
        //TODO: think about implicit conversion here
        currentState = newState
    }

}

/** wrapper for BotActorImplementation that will be exposed for users **/
class Bot(botName: String) {
    private val statesToHandlers = collection.mutable.Map[String, Behaviour]()
    private var botActor: ActorRef = null
    private val data: BotDataStorage = new BotDataStorage()

    def storesData[T](fieldName: String, value: T): Unit = {
        data.initHolder[T](fieldName, value)
    }

    var startState: String = null

    /** add new state to bot **/
    def +(newState: State) = {
        statesToHandlers += (newState.stateName -> newState.stateBehaviour)
        this
    }

    def startWith(s: State) = {
        startState = s.stateName
    }

    def launch(system: ActorSystem) = {
        if (startState == null) {
            throw new IllegalArgumentException(s"Bot $botName error when instantiating: starting state isn't declared")
        }

        botActor = system.actorOf(
            Props(classOf[BotActorImplementation], system, botName, statesToHandlers, startState, data),
            s"bot-$botName"
        )
    }
}

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