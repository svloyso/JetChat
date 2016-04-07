package actors

import akka.actor._
import scala.concurrent.duration.DurationInt

/**
  * Created by svloyso on 07.04.16.
  */

class EchoBot(system: ActorSystem, name: String) extends BotActor(system, name) with ActorLogging {

  override def botReceive: Receive = {
    case BotRecv(groupId, topicId, text) =>
      log.info(s"Bot with id $id got a message: $text")
      if ((id != -1) && (text contains "bot")) {
        log.info("Resend msg back to manager")
        sender ! BotSend(id, groupId, topicId, text)
      }
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
