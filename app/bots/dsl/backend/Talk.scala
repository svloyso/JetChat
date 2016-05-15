package bots.dsl.backend

import actors.MasterActor
import akka.actor.{ActorLogging, Actor, ActorRef}
import bots.dsl.backend.BotMessages._
import bots.dsl.frontend._

import scala.concurrent.duration.FiniteDuration

/**
  * Created by dsavvinov on 5/13/16.
  */
class Talk(
            val collocutor: User,
            val chat: ChatRoom,
            val statesToHandlers: collection.mutable.Map[String, Behaviour],
            val parent: ActorRef,
            var currentState: String,
            val data: BotDataStorage
          )
  extends MasterActor with ActorLogging {
  /** == internal methods and fields == **/
  /** bind handlers to this talk **/
  statesToHandlers.foreach { case (s, b) => b.bindToTalk(this) }

  override def receiveAsMaster = {
    case ScheduledTask(task) =>
      log.info ("Executing scheduled task")
      task()
    case msg: ChatMessage =>
      statesToHandlers.get(currentState) match {
        case Some(behaviour) =>
          log.info(s"""Got ChatMessage "$msg", passing to user-handler""")
          behaviour.handler(msg)
        case None => throw new IllegalStateException(s"In talk <$this> incorrect state <$currentState> encountered")
      }
  }

  /** == DSL-related methods == **/
  def say(text: String) = {
    log.info (s"Responding with message <$text>")
    parent ! SendToUser(ChatAddress(collocutor, chat), text)
  }

  def moveTo(newState: String) = {
    log.info(s"""Moving to State <$newState>""")
    currentState = newState
  }

  def broadcast(text: String) = {
    log.info(s"""Sending broadcast request for message <$text>""")
    parent ! BroadcastMessage(text)
  }

  def schedule(task: (Unit => Any), duration: FiniteDuration): Unit = {
    log.info(s"""Scheduling task""")
    import context._
    context.system.scheduler.scheduleOnce(duration, context.self, new ScheduledTask(task))
  }

  def getUserID: User = {
    collocutor
  }

  def sendToGlobal(message: Any) = {
    log.info(s"""Sending message <$message> to parent-actor""")
    parent ! message
  }
}