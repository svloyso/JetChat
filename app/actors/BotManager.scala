package actors

import java.sql.Timestamp
import java.util.Calendar

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import models._
import play.api.Logger
import play.api.libs.json.{Json, JsObject, JsString, JsNumber}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.duration.DurationInt
import scala.reflect.runtime._
import scala.tools.reflect.ToolBox

/**
  * Created by svloyso on 24.03.16.
  */
class BotManager (system: ActorSystem,
                  commentsDAO: CommentsDAO,
                  directMessagesDAO: DirectMessagesDAO,
                  usersDAO: UsersDAO, botsDAO: BotsDAO) extends MasterActor with ActorLogging {

  val mediator = DistributedPubSub(system).mediator
  var registeredBots: List[(User, ActorRef)] = Nil

  val commands: List[(String) => Boolean] = List[String => Boolean] (
    (s:String) => if (s == "test compiling") {
      BotCompiler.compileEchoBot(system)
      true
    } else { false }
  )

  override def preStart(): Unit = {
    super.preStart()

    log.info("Start to run bots from DB...")

    botsDAO.all() foreach {
      case seq => seq foreach {
          case Bot(userId, code, state, true) =>
            usersDAO.findById(userId) map {
              case Some(user) =>
                val botClass = compileBot(code)
                raiseBot(user, botClass, state)
              case None => log.error(s"Can not find user for bot with userId $userId")
            }
          case _ =>
        }
      }
    }

  def compileBot(botCode: String): Class[BotActor] = {
    val classLoader = getClass.getClassLoader
    val runtimeMirror = universe.runtimeMirror(classLoader)
    val toolBox = runtimeMirror.mkToolBox()
    toolBox.eval(toolBox.parse(botCode)).asInstanceOf[Class[BotActor]]
  }

  def createBotFromCode(userName: String, botCode: String) = {
    log.info(s"Creating bot $userName from code")
    val botClass = compileBot(botCode)
    usersDAO.mergeByLogin(login = userName, name = userName, isBot=true) map {
      case user =>
        botsDAO.findByUserId(user.id) map {
          case None =>
            botsDAO.insert(Bot(userId=user.id, code=botCode, state = None, isActive=true))
          case Some(_) =>
            log.error(s"Try to create already existent bot with userId: ${user.id}")
        }
        raiseBot(user, botClass)
    }
  }

  def raiseBot(botUser: User, botClass: Class[_ <: BotActor], state: Option[String] = None) = {
    val botActor = system.actorOf(Props(botClass, system, botUser, state))
    registerBot(botUser, botActor)
  }

  def registerBot(botUser: User, botActor: ActorRef) = {
    log.info(s"Got a RegisterBot message from $botUser")
    registeredBots = (botUser, botActor) :: registeredBots
    system.actorSelection("/user/online-user-registry") ! Tick(botUser.id)
  }


  override def receiveAsMaster: Receive = {
    case CreateBot(userName, code) =>
      createBotFromCode(userName, code)
    case RegisterBot(botUser, botActor) =>
      registerBot(botUser, botActor)
    case UnregisterBot(botId) =>
      log.info(s"Got a UnRegisterBot message from $botId")
      registeredBots = registeredBots.filterNot { case (u, _) => u.id == botId }
    case BotSend(botId, groupId, topicId, text) =>
      log.info(s"Bot ($botId) sends a message $text")
      val date = new Timestamp(Calendar.getInstance.getTime.getTime)
      commentsDAO.insert(Comment(groupId = groupId, userId = botId, topicId = topicId, date = date, text = text)).map { case id =>
        usersDAO.findById(botId).map { case option =>
          val user = option.get
          val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login))
          mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("id" -> JsNumber(id),
            "group" -> JsObject(Seq("id" -> JsNumber(groupId))),
            "topicId" -> JsNumber(topicId),
            "user" -> JsObject(userJson),
            "date" -> JsNumber(date.getTime),
            "text" -> JsString(text)))))
        }
        Results.Ok(Json.toJson(JsNumber(id)))
      }
    case MsgRecv(userId, groupId, topicId, text) =>
      if(!commands.map(cmd => cmd(text)).reduce(_ || _)) {
        log.info(s"Got a message $text. Check ${commands.size} commands and resend it to ${registeredBots.size} bots")
        for ((botUser, botActor) <- registeredBots) {
          log.info(s"Resend it to $botUser")
          botActor ! BotRecv(userId, groupId, topicId, text)
        }
      }
    case DirMsgRecv(fromId, toId, text) =>
      for((botUser, botActor) <- registeredBots) {
        if(botUser.id == toId) botActor ! BotDirRecv(fromId, text)
      }
    case BotDirSend(botId, userId, text) =>
      val date = new Timestamp(Calendar.getInstance.getTime.getTime)
      directMessagesDAO.insert(DirectMessage(fromUserId = botId, toUserId = userId, date = date, text = text)).flatMap {
        case id =>
          (for {
            user <- usersDAO.findById(botId)
            toUser <- usersDAO.findById(userId)
          } yield (user, toUser)).map { case (u, tU) =>
            val user = u.get
            val toUser = tU.get
            Logger.debug(s"Adding direct message: $botId, $userId, $text")
            val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login))
            val toUserJson = Seq("id" -> JsNumber(toUser.id), "name" -> JsString(toUser.name), "login" -> JsString(toUser.login))
            val message = JsObject(Seq("id" -> JsNumber(id),
              "user" -> JsObject(userJson),
              "toUser" -> JsObject(toUserJson),
              "date" -> JsNumber(date.getTime),
              "text" -> JsString(text)))
            mediator ! Publish("cluster-events", ClusterEvent(ActorUtils.encodePath(user.login), message))
            mediator ! Publish("cluster-events", ClusterEvent(ActorUtils.encodePath(toUser.login), message))
            Results.Ok(Json.toJson(JsNumber(id)))
          }
      }
    case GetUserByName(name) =>
      val botSender = sender
      usersDAO.findByLogin(name).map {
        case r => botSender ! r
      }
    case GetUserById(id) =>
      val botSender = sender
      usersDAO.findById(id).map {
        case r => botSender ! r
      }
    case GetUserList =>
      val botSender = sender
      usersDAO.all.map {
        case s: Seq[User] => botSender ! s
      }
    case UpdateState(botId, newState) =>
      log.info(s"Updating state of bot $botId: $newState")
      botsDAO.updateState(botId, newState)
  }
}

sealed trait BotMessage
case class CreateBot(userName: String, code: String) extends BotMessage
case class RegisterBot(botUser: User, botActor: ActorRef) extends BotMessage
case class UnregisterBot(botId: Long) extends BotMessage
case class MsgRecv(userId: Long, groupId: Long, topicId: Long, text: String) extends BotMessage
case class DirMsgRecv(fromId: Long, toId: Long, text: String) extends BotMessage
case class BotSend(botId: Long, groupId: Long, topicId: Long, text: String) extends BotMessage
case class BotRecv(userId: Long, groupId: Long, topicId: Long, text: String) extends BotMessage
case class BotDirRecv(fromId: Long, text: String) extends BotMessage
case class BotDirSend(botId: Long, userId: Long, text: String) extends BotMessage
case class GetUserList() extends BotMessage
case class GetUserByName(name: String) extends BotMessage
case class GetUserById(id: Long) extends BotMessage
case class UpdateState(botId: Long, newState: String) extends BotMessage

object BotManager {
  val DEFAULT_DURATION = 30.seconds

  val actorName = "BotManager"

  def actorOf(system: ActorSystem,
              commentsDAO: CommentsDAO,
              directMessagesDAO: DirectMessagesDAO,
              usersDAO: UsersDAO, botsDAO: BotsDAO): ActorRef =
    system.actorOf(Props(new BotManager(system, commentsDAO, directMessagesDAO, usersDAO, botsDAO)),
      actorName)

  def actorSelection(system: ActorSystem): ActorSelection =
    system.actorSelection("/user/" ++ actorName)
}

