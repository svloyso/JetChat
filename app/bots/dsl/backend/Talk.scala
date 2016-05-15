package bots.dsl.backend

import akka.actor.{Actor, ActorRef}
import bots.dsl.backend.BotMessages._
import bots.dsl.frontend._

import scala.concurrent.duration.{FiniteDuration, Duration}

/**
  * Created by dsavvinov on 5/13/16.
  */
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
  /** == internal methods and fields == **/


  /** bind handlers to this talk **/
  statesToHandlers.foreach { case (s, b) => b.bindToTalk(this) }

  def receive = {
    case ScheduledTask(task) =>
      task()
    case textMessage: TextMessage =>
      statesToHandlers.get(currentState) match {
        case Some(behaviour) =>
          behaviour.handler(textMessage)
        case None => throw new IllegalStateException(s"In talk <$this> incorrect state <$currentState> encountered")
      }
  }

  /** == DSL-related methods == **/
  def say(text: String) = {
    parent ! SendToUser(userId, groupId, topicId, text)
  }

  def moveTo(newState: String) = {
    currentState = newState
  }

  def broadcast(text: String) = {
    parent ! BroadcastMessage(text)
  }

  def schedule(task: (Unit => Any), duration: FiniteDuration): Unit = {
    import context._
    context.system.scheduler.scheduleOnce(duration, context.self, new ScheduledTask(task))
  }

  def getUserID: Long = {
    userId
  }

  def sendToGlobal(message: Any) = {
    parent ! message
  }
}