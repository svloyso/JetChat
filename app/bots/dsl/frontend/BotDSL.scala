package bots.dsl.frontend

import akka.actor._
import bots.dsl.backend.BotMessages._
import bots.dsl.backend._

/** collection of proxy objects for unbound-DSL calls **/
trait Behaviour {
  var talk: Talk = null
  val data: DynamicProxyStorage = new DynamicProxyStorage()

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

  def moveTo(newState: String): Unit = {
    talk.moveTo(newState)
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

  /** for internal use only! Creates BotActor in Akka Actor System **/
  def launch(system: ActorSystem) = {
    if (startState == null) {
      throw new IllegalArgumentException(s"Bot $botName error when instantiating: starting state isn't declared")
    }

    botActor = system.actorOf(
      Props(classOf[bots.dsl.backend.BotActorImplementation], system, botName, statesToHandlers, startState, data),
      s"bot-$botName"
    )
  }
}