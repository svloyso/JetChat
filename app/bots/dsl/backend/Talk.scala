package bots.dsl.backend

import akka.actor.{Actor, ActorRef}
import bots.dsl.backend.BotMessages._
import bots.dsl.frontend._

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
    case textMessage: TextMessage =>
      println(s"in state $currentState processing message ${textMessage.text}")
      statesToHandlers.get(currentState) match {
        case Some(behaviour) =>
          behaviour.handler(textMessage)
        case None => throw new IllegalStateException(s"In talk <$this> incorrect state <$currentState> encountered")
      }
  }

  /** == DSL-related methods == **/
  def say(text: String) = {
    parent ! BotInternalOutcomingMessage(userId, groupId, topicId, text)
  }

  def moveTo(newState: String) = {
    currentState = newState
  }
}