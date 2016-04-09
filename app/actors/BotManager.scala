package actors

import java.sql.Timestamp
import java.util.Calendar

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import api.{DummyClass, Bot, BotInternalOutcomingMessage, TextMessage}
import models.{User, Comment, UsersDAO, CommentsDAO}
import play.api.Logger
import play.api.libs.json.{JsObject, JsString, JsNumber}
import play.api.libs.concurrent.Execution.Implicits._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.reflect.runtime._
import scala.tools.reflect.ToolBox
/**
  * Created by svloyso on 24.03.16.
  */
class BotManager (system: ActorSystem,
                  commentsDAO: CommentsDAO,
                  usersDAO: UsersDAO) extends MasterActor with ActorLogging {

  val mediator = DistributedPubSub(system).mediator
  val registeredBots: ArrayBuffer[ActorRef] = new ArrayBuffer[ActorRef]

  //TODO: Debugging only! Remove ASAP
  val handler : String =
    """
      def receive : ActorRef => Actor.Receive = (sender : ActorRef) => {
              case TextMessage(senderId, groupId, topicId, text) =>
                   sender ! BotInternalOutcomingMessage(senderId, groupId, topicId,
                       s"Recieved message from $senderId in group $groupId and topic $topicId " + "\n" +
                           s"Message: $text")
          }
    """

  val commands: List[(String) => Boolean] = List[String => Boolean] (
    (s:String) => if (s == "test compiling") { BotCompilerTest(system, handler); true } else { false }
  )

  override def receiveAsMaster: Receive = {
    case RegisterBot(botName, botAvatar) =>
      log.info("Got an RegisterBot message")
      val botSender = sender
      usersDAO.mergeByLogin(botName, botName, botAvatar).map {
        case User(id, _, _, _, _) =>
          registeredBots += botSender //TODO: potential race condition. Need to be fixed
          botSender ! id
      }
    case BotSend(botId, groupId, topicId, text) =>
      log.info(s"Bot ($botId) sends a message $text")
      val date = new Timestamp(Calendar.getInstance.getTime.getTime)
      commentsDAO.insert(Comment(groupId = groupId, userId = botId, topicId = topicId, date = date, text = text)).map { case id =>
        val userJson = Seq("id" -> JsNumber(botId), "name" -> JsString("Bot"), "login" -> JsString("Bot"))
        mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("id" -> JsNumber(id),
          "group" -> JsObject(Seq("id" -> JsNumber(groupId))),
          "topicId" -> JsNumber(topicId),
          "user" -> JsObject(userJson),
          "date" -> JsNumber(date.getTime),
          "text" -> JsString(text)))))
      }
    case MsgRecv(userId, groupId, topicId, text) =>
      log.info(s"Got a message $text. Check ${commands.size} commands and resend it to ${registeredBots.size} bots")
      if(!commands.map(cmd => cmd(text)).reduce(_ || _)) {
        for (
          bot <- registeredBots
        ) {
          log.info(s"Resend it to ${bot.toString()}")
          bot ! BotRecv(userId, groupId, topicId, text)
        }
      }
    case GetUserList =>
      val botSender = sender
      usersDAO.all.map {
        case s: Seq[User] => botSender ! s
      }
  }
}

case class RegisterBot(botName: String, botAvatar: Option[String])
case class MsgRecv(userId: Long, groupId: Long, topicId: Long, text: String)
case class BotSend(botId: Long, groupId: Long, topicId: Long, text: String)
case class BotRecv(userId: Long, groupId: Long, topicId: Long, text: String)
case class GetUserList()

object BotManager {
  val DEFAULT_DURATION = 30.seconds

  val actorName = "BotManager"

  def actorOf(system: ActorSystem,
              commentsDAO: CommentsDAO,
              usersDAO: UsersDAO): ActorRef =
    system.actorOf(Props(new BotManager(system, commentsDAO, usersDAO)),
      actorName)

  def actorSelection(system: ActorSystem): ActorSelection =
    system.actorSelection("/user/" ++ actorName)
}

object BotCompilerTest {
  def createBot(system: ActorSystem, botCode: String, botName:String) = {
    Logger.info(s"Creating a bot with name $botName")

    val cm      = universe.runtimeMirror(getClass.getClassLoader)
    val tb      = cm.mkToolBox()
    val handlerClass = tb.eval(tb.parse(botCode)).asInstanceOf[Class[DummyClass]]
    val handlerObj = handlerClass.getConstructors()(0).newInstance()
    val handler = handlerObj.asInstanceOf[DummyClass].apply()
    system.actorOf(Props(classOf[Bot], system, botName, handler))
  }

  def apply(system: ActorSystem, userSpecifiedHandler : String) = {
    createBot(system,
              """
                    import actors._
                    import akka.actor._
                    import scala.concurrent.duration.DurationInt
                    import scala.reflect.runtime
                    import scala.reflect.runtime._
                    import scala.reflect.runtime.universe._
                    import api.{DummyClass, TextMessage, BotInternalOutcomingMessage}

                    class RuntimeDummy extends DummyClass {

              """
                    +
                    userSpecifiedHandler
                    +
              """
                    override def apply() = receive
                    }
                    scala.reflect.classTag[RuntimeDummy].runtimeClass
              """,
              "CompiledBot")
  }
}