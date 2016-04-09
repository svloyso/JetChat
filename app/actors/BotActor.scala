package actors

import akka.actor._

abstract class BotActor(system: ActorSystem, name: String, avatar: Option[String] = None) extends Actor with ActorLogging {
  val manager = BotManager.actorSelection(system)
  var id: Long = -1

  final override def preStart: Unit = {
    log.info(s"New bot with name $name tries to register")
    manager ! RegisterBot(name, avatar)
  }

  final override def receive: Receive = {
    case BotRegistered(myId) =>
      log.info(s"New bot was registered with id $myId")
      id = myId
      botStart()
    case BotRecv(userId, groupId, topicId, text) =>
      log.info(s"Bot with id $id got a message: $text")
      if (isRegistered) {
        botReceive(userId, groupId, topicId, text)
      }
  }

  def send(groupId: Long, topicId: Long, text: String): Unit = manager ! BotSend(id, groupId, topicId, text)

  def isRegistered: Boolean = id != -1

  def botStart(): Unit = {}
  def botReceive(userId: Long, groupId: Long, topicId: Long, text: String): Unit

}
