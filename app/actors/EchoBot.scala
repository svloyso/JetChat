package actors

import akka.actor._
import scala.concurrent.duration.DurationInt
import models.User


/**
  * Created by svloyso on 07.04.16.
  */

class EchoBot(system: ActorSystem, user: User) extends BotActor(system, user) with ActorLogging {
  val UserList  = "(userlist)".r
  val MsgTo     = "to (.*): (.*)".r

  override def receiveMsg(userId: Long, groupId: Long, topicId: Long, text: String) = text match {
    case UserList(_) => send(groupId, topicId, getUserList.map((u:User) => u.login).mkString(", "))
    case MsgTo(to, msg) => sendDirect(getUserByName(to).get.id, msg)
    case msg => send(groupId, topicId, text)
  }
  override def receiveDirect(userId: Long, text: String) = {
    sendDirect(userId, text)
  }
}

object EchoBot {
  val DEFAULT_DURATION = 30.seconds

  val actorName = "EchoBot"

  def actorOf(system: ActorSystem, user: User): ActorRef =
    system.actorOf(Props(classOf[EchoBot], system, user), actorName)

  def actorSelection(system: ActorSystem): ActorSelection =
    system.actorSelection("/user/" ++ actorName)
}
