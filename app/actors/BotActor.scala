package actors

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import bots.dsl.backend.BotMessages._
import scala.concurrent.Await
import scala.concurrent.duration._
import models.User

import scala.language.postfixOps

abstract class BotActor(system: ActorSystem, user: User, state: Option[String] = None) extends Actor with ActorLogging {
  val manager = BotManager.actorSelection(system)
  implicit val timeout = Timeout(5 seconds)

  final override def preStart: Unit = botStart()

  final override def receive: Receive = {
    case BotRecv(userId, groupId, topicId, text) =>
      log.info(s"Bot with id $id got a message: '$text'")
      receiveMsg(TextMessage(ChatAddress(userId, ChatRoom(groupId, topicId)), text))
    case BotDirRecv(userId: Long, text: String) =>
      log.info(s"Bot with id $id got a direct message '$text' from user $userId")
      receiveDirect(PrivateMessage(userId, text))
    case otherMsg => receiveOther(otherMsg)
  }

  def updateState(newState: String) = manager ! UpdateState(user.id, newState)

  def send(groupId: Long, topicId: Long, text: String): Unit = manager ! BotSend(user.id, groupId, topicId, text)
  def sendDirect(userId: Long, text: String): Unit = manager ! BotDirSend(user.id, userId, text)
  def getUserList: Seq[User] = Await.result(manager ? GetUserList, timeout.duration).asInstanceOf[Seq[User]]
  def getUserByName(name: String): Option[User] = Await.result(manager ? GetUserByName(name), timeout.duration).asInstanceOf[Option[User]]
  def getUserById(id: Long): Option[User] = Await.result(manager ? GetUserById(id), timeout.duration).asInstanceOf[Option[User]]

  def botStart(): Unit = {}
  def receiveMsg(message: ChatMessage): Unit = {}
  def receiveDirect(message: PrivateMessage): Unit = {}
  def receiveOther(msg: Any): Unit = {}
}
