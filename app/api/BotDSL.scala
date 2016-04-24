package api

import actors.BotActor
import akka.actor._

/**
  * Created by dsavvinov on 4/7/16.
  */


//TODO: is this hierarchy good?
trait Message

trait InternalMessage extends Message

trait ChatMessage extends Message

case class TextMessage(senderId: Long, groupId: Long, topicId: Long, text: String) extends ChatMessage

case class BotInternalOutcomingMessage(adresseeId: Long, groupId: Long, topicId: Long, text: String) extends InternalMessage

//TODO: think if we can remove boiler-plate 'new' from the DSL (maybe using companion-objects?..)
trait Behaviour[DataType] {
    var talk: Talk[DataType] = null

    def handler(msg: TextMessage): Unit

    def bindToTalk(t: Talk[DataType]) = {
        talk = t
    }

    /** redirecting calls to Talk **/
    def say(text: String): Unit = {
        talk.say(text)
    }

    def moveTo(newState: String): Unit = {
        talk.moveTo(newState)
    }

    def data() : DataType = {
        talk.getData
    }
}

abstract class AbstractData() {

}

object State {
    def apply[T](stateName: String)(stateBehaviour: Behaviour[T]): State[T] = {
        new State(stateName)(stateBehaviour)
    }
}

class State[T](val stateName: String)(val stateBehaviour: Behaviour[T]) {}

class Talk[DataType](
                                        val userId: Long,
                                        val groupId: Long,
                                        val topicId: Long,
                                        val statesToHandlers: collection.mutable.Map[String, Behaviour[DataType]],
                                        val parent: ActorRef,
                                        var currentState: String,
                                        val data: DataType
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

    def getData = {
        data
    }

    def moveTo(newState: String) = {
        //TODO: think about implicit conversion here
        currentState = newState
    }

}

/** wrapper for BotActorImplementation that will be exposed for users **/
class Bot[T](botName: String, val initialData : T) {
    private val statesToHandlers = collection.mutable.Map[String, Behaviour[T]]()
    private var botActor: ActorRef = null

    var startState: String = null

    /** add new state to bot **/
    def +(newState: State[T]) = {
        statesToHandlers += (newState.stateName -> newState.stateBehaviour)
        this
    }

    def startWith(s: State[T]) = {
        startState = s.stateName
    }

    def launch(system: ActorSystem) = {
        if (startState == null) {
            throw new IllegalArgumentException(s"Bot $botName error when instantiating: starting state isn't declared")
        }

        botActor = system.actorOf(
            Props(classOf[BotActorImplementation[T]], system, botName, statesToHandlers, startState, initialData),
            s"bot-$botName"
        )
    }
}

class BotActorImplementation[T](
                                   system: ActorSystem,
                                   name: String,
                                   statesToHandlers: collection.mutable.Map[String, Behaviour[T]],
                                   startingState: String,
                                   initialData : T
                               ) extends BotActor(system, name) {

    private val usersToTalks: collection.mutable.Map[Long, ActorRef] = collection.mutable.Map()

    override def receiveMsg(senderId: Long, groupId: Long, topicId: Long, text: String): Unit = {
        usersToTalks.get(senderId) match {
            case Some(talk) => talk ! TextMessage(senderId, groupId, topicId, text)
            case None =>
                val talkHandlers = statesToHandlers.clone()
                val newTalk = context.actorOf(
                    Props(classOf[Talk[T]], senderId, groupId, topicId, talkHandlers, self, startingState, initialData),
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