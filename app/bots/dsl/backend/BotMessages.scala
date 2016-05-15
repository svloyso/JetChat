package bots.dsl.backend

/**
  * Created by dsavvinov on 5/13/16.
  */

package object BotMessages {

  trait Message

  trait InternalMessage extends Message
  trait ChatMessage extends Message

  case class TextMessage(senderId: Long, groupId: Long, topicId: Long, text: String) extends ChatMessage

  case class SendToUser(adresseeId: Long, groupId: Long, topicId: Long, text: String) extends InternalMessage
  case class BroadcastMessage(test: String) extends InternalMessage
  case class ScheduledTask(callable: (Unit => Any)) extends InternalMessage
}