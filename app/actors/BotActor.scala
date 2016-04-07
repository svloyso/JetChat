package actors

import akka.actor._

abstract class BotActor(system: ActorSystem, name: String, avatar: Option[String] = None) extends Actor with ActorLogging {
  val manager = BotManager.actorSelection(system)
  var id: Long = -1

  final override def preStart: Unit = {
    log.info(s"New bot with name $name tries to register")
    manager ! RegisterBot(name, avatar)
    botStart()
  }

  final override def receive: Receive = {
    case BotRegistered(myId) =>
      log.info(s"New bot was registered with id $myId")
      id = myId
    case msg => botReceive(msg)
  }

  def botReceive: Receive
  def botStart(): Unit = {}
}

