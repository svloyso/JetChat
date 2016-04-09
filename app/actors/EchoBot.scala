package actors

import akka.actor._
import scala.concurrent.duration.DurationInt
import models.User
import util.matching.Regex


/**
  * Created by svloyso on 07.04.16.
  */

class EchoBot(system: ActorSystem, name: String) extends BotActor(system, name) with ActorLogging {
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

  def actorOf(system: ActorSystem): ActorRef =
    system.actorOf(Props(new EchoBot(system, actorName)),
      actorName)

  def actorSelection(system: ActorSystem): ActorSelection =
    system.actorSelection("/user/" ++ actorName)
}
