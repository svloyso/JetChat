package bots.dsl.frontend

import akka.actor._
import bots.dsl.backend.BotMessages._
import bots.dsl.backend._
import scala.concurrent.duration.{FiniteDuration, Duration}

/** collection of proxy objects for unbound-DSL calls **/
trait Behaviour {
  var talk: Talk = null
  val data: LocalDynamicProxyStorage = new LocalDynamicProxyStorage()

  /** user-defined function for handling incoming messages **/
  def handler(msg: TextMessage): Unit

  def bindToTalk(t: Talk) = {
    talk = t
    data.talk = t
  }

  /** redirecting calls to Talk **/
  def say(text: String): Unit = {
    talk.say(text)
  }

  def broadcast(message: String): Unit = {
    talk.broadcast(message)
  }

  def moveTo(newState: String): Unit = {
    talk.moveTo(newState)
  }

  def schedule(task: (Unit => Any), duration: FiniteDuration): Unit = {
    talk.schedule(task, duration)
  }

  def sendToGlobal(message: Any) = {
    talk.sendToGlobal(message)
  }

  def getUserID: Long = {
    talk.getUserID
  }
}

trait GlobalBehaviour {
  var botImpl: BotActorImplementation = null
  var globalStorage: BotDataStorage = new BotDataStorage()
  var talkCreatedProcessor: (Long => Unit) = x => Unit
  var messageReceiveProcessor: PartialFunction[Any, Unit] = PartialFunction.empty[Any, Unit]
  def sendTo(userID: Long, text: String) = {
    botImpl.sendTo(userID, text)
  }

  def schedule(task: (Unit => Any), duration: FiniteDuration): Unit = {
    botImpl.schedule(task, duration)
  }

  def bindToBotActor(b: BotActorImplementation): Unit = {
    botImpl = b
  }

  def onTalkCreation(handler: Long => Unit): Unit = {
    talkCreatedProcessor = handler
  }

  def onMessageReceive(handler: PartialFunction[Any, Unit]) = {
    messageReceiveProcessor = handler
  }
}

/** companion object for removing redundant "new" from user DSL **/
object State {
  def apply(stateName: String)(stateBehaviour: Behaviour): State = {
    new State(stateName)(stateBehaviour)
  }
}

class State(val stateName: String)(val stateBehaviour: Behaviour) {}

/** wrapper for BotActorImplementation that will be exposed for users **/
class Bot(botName: String) {
  private val statesToHandlers = collection.mutable.Map[String, Behaviour]()
  private var botActor: ActorRef = null
  private var globalBehaviour: GlobalBehaviour = new GlobalBehaviour {}

  val data: BotDataStorage = new BotDataStorage()

  def storesData[T](fieldName: String): DataTransformer[T] = {
    new DataTransformer[T](fieldName, data)
  }

  def storesGlobal[T](fieldName: String): DataTransformer[T] = {
    new DataTransformer[T](fieldName, globalBehaviour.globalStorage)
  }

  var startState: String = null

  def startWith(s: State) = {
    startState = s.stateName
  }

  /** add new state to bot **/
  def +(newState: State) = {
    statesToHandlers += (newState.stateName -> newState.stateBehaviour)
    this
  }

  def overrideGlobal(b: GlobalBehaviour) = {
    b.globalStorage = globalBehaviour.globalStorage
    globalBehaviour = b
  }

  /** for internal use only! Creates BotActor in Akka Actor System **/
  def launch(system: ActorSystem) = {
    if (startState == null) {
      throw new IllegalArgumentException(s"Bot $botName error when instantiating: starting state isn't declared")
    }

    botActor = system.actorOf(
      Props(classOf[bots.dsl.backend.BotActorImplementation], system, botName, statesToHandlers, startState, data,
        globalBehaviour),
      s"bot-$botName"
    )
  }
}