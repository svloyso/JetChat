package actors

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import models.User

abstract class BotActor(system: ActorSystem, name: String, avatar: Option[String] = None) extends Actor with ActorLogging {
  val manager = BotManager.actorSelection(system)
  implicit val timeout = Timeout(5 seconds)
  val id: Long = Await.result(manager ? CreateBot(name, avatar), timeout.duration).asInstanceOf[Long]

  final override def preStart: Unit = {
    manager ! RegisterBot(getUserById(id).get)
    botStart()
  }
  final override def receive: Receive = {
    case BotRecv(userId, groupId, topicId, text) =>
      log.info(s"Bot with id $id got a message: '$text'")
      receiveMsg(userId, groupId, topicId, text)
    case BotDirRecv(userId: Long, text: String) =>
      log.info(s"Bot with id $id got a direct message '$text' from user $userId")
      receiveDirect(userId, text)
    case otherMsg => receiveOther(otherMsg)
  }

  def send(groupId: Long, topicId: Long, text: String): Unit = manager ! BotSend(id, groupId, topicId, text)
  def sendDirect(userId: Long, text: String): Unit = manager ! BotDirSend(id, userId, text)
  def getUserList: Seq[User] = Await.result(manager ? GetUserList, timeout.duration).asInstanceOf[Seq[User]]
  def getUserByName(name: String): Option[User] = Await.result(manager ? GetUserByName(name), timeout.duration).asInstanceOf[Option[User]]
  def getUserById(id: Long): Option[User] = Await.result(manager ? GetUserById(id), timeout.duration).asInstanceOf[Option[User]]

  def botStart(): Unit = {}
  def receiveMsg(userId: Long, groupId: Long, topicId: Long, text: String): Unit = {}
  def receiveDirect(userId: Long, text: String): Unit = {}
  def receiveOther: Receive = {case _ => Unit}
}
