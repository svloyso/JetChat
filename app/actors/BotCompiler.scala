package actors

import akka.actor.ActorSystem

import scala.reflect.runtime._
import scala.tools.reflect.ToolBox

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


      class EchoBotRuntime(system: ActorSystem, user: User) extends BotActor(system, user) with ActorLogging {
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
      scala.reflect.classTag[EchoBotRuntime].runtimeClass
    """
  def compileEchoBot(system: ActorSystem) = {
    BotManager.actorSelection(system) ! CreateBot(userName = echoBotName, code = echoBotCode)
  }
}