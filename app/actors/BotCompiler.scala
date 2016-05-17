package actors

import akka.actor.ActorSystem


/**
  * Created by svloyso on 16.04.16.
  */
object BotCompiler {
  val echoBotName = "EchoBotRuntime"
  val echoBotCode =
    """
      import actors._
      import akka.actor._
      import scala.concurrent.duration.DurationInt
      import models.User


      class EchoBotRuntime(system: ActorSystem, user: User, state: Option[String] = None) extends BotActor(system, user, state) with ActorLogging {
        val UserList  = "(userlist)".r
        val MsgTo     = "to (.*): (.*)".r

        var counter = state.orElse(Some("0")).get.toLong

        override def receiveMsg(userId: Long, groupId: Long, topicId: Long, text: String) = text match {
          case UserList(_) => send(groupId, topicId, getUserList.map((u:User) => u.login).mkString(", "))
          case MsgTo(to, msg) => sendDirect(getUserByName(to).get.id, msg)
          case msg =>
            send(groupId, topicId,  s"$counter: $msg")
            updateState((counter + 1).toString)
            counter += 1
        }
        override def receiveDirect(userId: Long, text: String) = {
          sendDirect(userId, s"$counter: $text")
          updateState((counter + 1).toString)
          counter += 1
        }
      }
      scala.reflect.classTag[EchoBotRuntime].runtimeClass
    """
  def compileEchoBot(system: ActorSystem) = {
    BotManager.actorSelection(system) ! CreateBot(userName = echoBotName, code = echoBotCode)
  }
}