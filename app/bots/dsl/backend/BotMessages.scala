package bots.dsl.backend

/**
  * Created by dsavvinov on 5/13/16.
  */

package object BotMessages {

  trait Message

  trait InternalMessage extends Message // implementation-defined messages

  trait ChatMessage extends Message { // chat-related messages
    val address: ChatAddress
    val isPrivate: Boolean
  }

  trait UserMessage extends Message     // User-Defined message

  case class TextMessage(override val address: ChatAddress, text: String) extends ChatMessage {
    override val isPrivate = false
  }

  case class PrivateMessage(val user: User, text: String) extends ChatMessage {
    override val address = ChatAddress(user, null)
    override val isPrivate = true
  }

  case class SendToUser(address: ChatAddress, text: String) extends InternalMessage
  case class BroadcastMessage(test: String) extends InternalMessage
  case class ScheduledTask(callable: (Unit => Any)) extends InternalMessage

  case class User(id: Long)
  case class Group(id: Long)
  case class Topic(id: Long)
  case class ChatAddress(user: User, chatRoom: ChatRoom)
  case class ChatRoom(group: Group, topic: Topic)

  implicit def longToUser(id: Long): User = User(id)
  implicit def userToLong(user: User): Long = user.id
  implicit def longToGroup(id: Long): Group = Group(id)
  implicit def groupToLong(group: Group): Long = group.id
  implicit def longToTopic(id: Long): Topic = Topic(id)
  implicit def topicToLong(topic: Topic): Long = topic.id
}