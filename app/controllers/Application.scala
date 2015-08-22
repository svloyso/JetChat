package controllers

import java.sql.Timestamp
import java.util.Calendar
import javax.inject.{Inject, Singleton}

import actors.{ClusterEvent, WebSocketActor}
import akka.actor.{ActorSystem, PoisonPill}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import models._
import play.api.Logger
import play.api.libs.iteratee.{Concurrent, Iteratee}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Singleton
class Application @Inject()(val system: ActorSystem, val auth: Auth,
                            val usersDAO: UsersDAO, val groupsDAO: GroupsDAO,
                            val topicsDAO: TopicsDAO, val commentsDAO: CommentsDAO,
                            val directMessagesDAO: DirectMessagesDAO) extends Controller {

  implicit val tsReads: Reads[Timestamp] = Reads.of[Long] map (new Timestamp(_))
  implicit val tsWrites: Writes[Timestamp] = Writes { (ts: Timestamp) => JsString(ts.toString) }

  implicit val userReads: Reads[User] = (
    (JsPath \ "id").read[Long] and
    (JsPath \ "login").read[String] and
    (JsPath \ "name").read[String] and
    (JsPath \ "avatar").readNullable[String]
  )(User.apply _)
  implicit val userWrites: Writes[User] = (
    (JsPath \ "id").write[Long] and
    (JsPath \ "login").write[String] and
    (JsPath \ "name").write[String] and
    (JsPath \ "avatar").writeNullable[String]
  )(unlift(User.unapply))

  implicit val topicReads: Reads[Topic] = (
    (JsPath \ "id").read[Long] and
    (JsPath \ "groupId").read[Long] and
    (JsPath \ "userId").read[Long] and
    (JsPath \ "date").read[Timestamp] and
    (JsPath \ "text").read[String]
  )(Topic.apply _)
  implicit val topicWrites: Writes[Topic] = (
    (JsPath \ "id").write[Long] and
    (JsPath \ "groupId").write[Long] and
    (JsPath \ "userId").write[Long] and
    (JsPath \ "date").write[Timestamp] and
    (JsPath \ "text").write[String]
  )(unlift(Topic.unapply))

  val TICK = JsString("Tick")
  val TACK = JsString("Tack")

  val mediator = DistributedPubSub(system).mediator

  var actorCounter = 0

  def index(groupId: Option[Long] = None, topicId: Option[Long] = None, userId: Option[Long] = None ) = Action.async { implicit request =>
    request.cookies.get("user") match {
      case Some(cookie) =>
        usersDAO.findByLogin(cookie.value).flatMap {
          case Some(user) =>
            val webSocketUrl = routes.Application.webSocket(user.login).absoluteURL().replaceAll("http", "ws")
            (for {
              users <- getUsersJsValue(user.id)
              groups <- getGroupsJsValue(user.id)
              topic <- topicId match { case Some(value) => topicsDAO.findById(value) case None => Future { None }}
            } yield (users, groups, topic)) map { case (users, groups, topic) =>
              Ok(views.html.index(user, users, groups, webSocketUrl, groupId,
                topic match { case Some(value) => Some(Json.toJson(value)) case None => None }, userId))
            }
          case None =>
            Future.successful(Redirect(auth.getAuthUrl).discardingCookies(DiscardingCookie("user")))
        }
      case None =>
        if (auth.HUB_BASE_URL.nonEmpty) {
          Future.successful(Redirect(auth.getAuthUrl))
        } else {
          usersDAO.findByLogin(auth.HUB_MOCK_LOGIN).flatMap {
            case Some(user) =>
              Future.successful(Redirect(controllers.routes.Application.index(groupId, topicId, userId))
                .withCookies(Cookie("user", auth.HUB_MOCK_LOGIN, httpOnly = false)))
            case None =>
              usersDAO.insert(User(login = auth.HUB_MOCK_LOGIN, name = auth.HUB_MOCK_NAME,
                avatar = Option(auth.HUB_MOCK_AVATAR))).map { case id =>
                mediator ! Publish("cluster-events", ClusterEvent("*",
                  JsObject(Seq("newUser" -> JsObject(Seq("id" -> JsNumber(id),
                    "name" -> JsString(auth.HUB_MOCK_NAME), "login" -> JsString(auth.HUB_MOCK_LOGIN),
                    "avatar" -> JsString(auth.HUB_MOCK_AVATAR)))))))
                Redirect(controllers.routes.Application.index(groupId, topicId, userId))
                  .withCookies(Cookie("user", auth.HUB_MOCK_LOGIN, httpOnly = false))
              }
          }
        }
    }
  }

  def webSocket(login: String) = WebSocket.using[JsValue] { request =>
    val (out, channel) = Concurrent.broadcast[JsValue]

    actorCounter += 1
    val actor = system.actorOf(WebSocketActor.props(channel), s"$login.$actorCounter")

    val in = Iteratee.foreach[JsValue] { message =>
      if (message.equals(TICK))
        channel.push(TACK)
    } map { _ =>
      actor ! PoisonPill
    }

    (in, out)
  }

  def logout() = Action.async { implicit request =>
    Future.successful(Redirect(auth.getLogoutUrl).discardingCookies(DiscardingCookie("user")))
  }

  def getUser(login: String) = Action.async { implicit request =>
    usersDAO.findByLogin(login).map {
      case Some(user) => Ok(Json.toJson(user))
      case None => NoContent
    }
  }

  def getUsersJsValue(userId: Long): Future[JsValue] = {
    usersDAO.all.map { case users => Json.toJson(users) }
  }

  def getGroups(userId: Long) = Action.async { implicit request =>
    getGroupsJsValue(userId).map(Ok(_))
  }

  def getGroupsJsValue(userId: Long): Future[JsValue] = {
    groupsDAO.allWithCounts(userId).map { f =>
      Json.toJson(JsArray(f.map { case (group, count) => JsObject(Seq("id" -> JsNumber(group.id),
        "name" -> JsString(group.name), "count" -> JsNumber(count)))
      }))
    }
  }

  def getAllTopics(userId: Long) = Action.async { implicit request =>
    topicsDAO.allWithCounts(userId, None).map { f =>
      Json.toJson(JsArray(f.map { case (topicId, topicDate, topicText, gId, groupName, uId, userName, c) =>
        JsObject(Seq("topic" -> JsObject(Seq("id" -> JsNumber(topicId), "date" -> Json.toJson(topicDate), "group" -> JsObject
        (Seq("id" -> JsNumber(gId), "name" -> JsString(groupName))),
          "text" -> JsString(topicText), "user" -> JsObject(Seq("id" -> JsNumber(uId), "name" -> JsString(userName))))), "messages" -> JsNumber(c)))
      }))
    }.map(Ok(_))
  }

  def getGroupTopics(userId: Long, groupId: Long) = Action.async { implicit rs =>
    topicsDAO.allWithCounts(userId, Some(groupId)).map { f =>
      Json.toJson(JsArray(f.map { case (topicId, topicDate, topicText, gId, groupName, uId, userName, c) =>
        JsObject(Seq("topic" -> JsObject(Seq("id" -> JsNumber(topicId), "date" -> Json.toJson(topicDate), "group" -> JsObject
        (Seq("id" -> JsNumber(gId), "name" -> JsString(groupName))),
          "text" -> JsString(topicText), "user" -> JsObject(Seq("id" -> JsNumber(uId), "name" -> JsString(userName))))), "messages" -> JsNumber(c)))
      }))
    }.map(Ok(_))
  }

  def getMessages(userId: Long, topicId: Long) = Action.async { implicit request =>
    topicsDAO.messages(userId, topicId).map { f =>
      Ok(Json.toJson(JsArray(f.map { case (message, user, group) =>
        val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
          (user.avatar match {
            case Some(value) => Seq("avatar" -> JsString(value))
            case None => Seq()
          })
        val fields = Seq("id" -> JsNumber(message.id),
          "group" -> JsObject(Seq("id" -> JsNumber(group.id), "name" -> JsString(group.name))),
          "user" -> JsObject(userJson),
          "date" -> JsNumber(message.date.getTime),
          "text" -> JsString(message.text)) ++ (message match {
          case c: Comment =>
            Seq("topicId" -> JsNumber(c.topicId))
          case _ => Seq()
        })
        JsObject(fields)
      })))
    }
  }

  def addGroup() = Action.async { implicit rs =>
    val groupName = rs.body.asJson.get.asInstanceOf[JsString].value
    groupsDAO.insert(Group(name = groupName)).map { case id =>
      Logger.debug(s"Adding group: $groupName")
      val groupJson = JsObject(Seq("id" -> JsNumber(id), "name" -> JsString(groupName)))
      mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("newGroup" -> groupJson))))
      Ok(Json.toJson(groupJson))
    }
  }


  def addComment() = Action.async(parse.json) { implicit request =>
    val userId = (request.body \ "user" \ "id").get.asInstanceOf[JsNumber].value.toLong
    val groupId = (request.body \ "groupId").get.asInstanceOf[JsNumber].value.toLong
    val topicId = (request.body \ "topicId").get.asInstanceOf[JsNumber].value.toLong
    val text = (request.body \ "text").get.asInstanceOf[JsString].value
    val date = new Timestamp(Calendar.getInstance.getTime.getTime)
    commentsDAO.insert(Comment(groupId = groupId, userId = userId, topicId = topicId, date = date, text = text)).flatMap { case id =>
      Logger.debug(s"Adding comment: $userId, $groupId, $topicId, $text")
      usersDAO.findById(userId).map { case option =>
        val user = option.get
        val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
          (user.avatar match {
            case Some(value) => Seq("avatar" -> JsString(value))
            case None => Seq()
          })
        mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("id" -> JsNumber(id),
          "groupId" -> JsNumber(groupId),
          "topicId" -> JsNumber(topicId),
          "user" -> JsObject(userJson),
          "date" -> JsNumber(date.getTime),
          "text" -> JsString(text)))))
        Ok(Json.toJson(JsNumber(id)))

      }
    }
  }

  def addDirectMessage() = Action.async(parse.json) { implicit request =>
    val fromUserId = (request.body \ "user" \ "id").get.asInstanceOf[JsNumber].value.toLong
    val toUserId = (request.body \ "toUser" \ "id").get.asInstanceOf[JsNumber].value.toLong
    val text = (request.body \ "text").get.asInstanceOf[JsString].value
    val date = new Timestamp(Calendar.getInstance.getTime.getTime)
    directMessagesDAO.insert(DirectMessage(fromUserId = fromUserId, toUserId = toUserId, date = date, text = text)).flatMap {
      case id =>
        (for {
          user <- usersDAO.findById(fromUserId)
          toUser <- usersDAO.findById(toUserId)
        } yield (user, toUser)).map { case (u, tU) =>
          val user = u.get
          val toUser = tU.get
          Logger.debug(s"Adding direct message: $fromUserId, $toUserId, $text")
          val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
            (user.avatar match {
              case Some(value) => Seq("avatar" -> JsString(value))
              case None => Seq()
            })
          val toUserJson = Seq("id" -> JsNumber(toUser.id), "name" -> JsString(toUser.name), "login" -> JsString(toUser.login)) ++
            (toUser.avatar match {
              case Some(value) => Seq("avatar" -> JsString(value))
              case None => Seq()
            })
          val message = JsObject(Seq("id" -> JsNumber(id),
            "user" -> JsObject(userJson),
            "toUser" -> JsObject(toUserJson),
            "date" -> JsNumber(date.getTime),
            "text" -> JsString(text)))
          mediator ! Publish("cluster-events", ClusterEvent(user.login, message))
          mediator ! Publish("cluster-events", ClusterEvent(toUser.login, message))
          Ok(Json.toJson(JsNumber(id)))
        }
    }
  }

  def getDirectMessages(fromUserId: Long, toUserId: Long) = Action.async { implicit request =>
    directMessagesDAO.messages(fromUserId, toUserId).map { case seq =>
      Ok(Json.toJson(JsArray(seq.map { case (message, fromUser, toUser) =>
        val fromUserJson = Seq("id" -> JsNumber(fromUser.id), "name" -> JsString(fromUser.name), "login" -> JsString(fromUser.login)) ++
          (fromUser.avatar match {
            case Some(value) => Seq("avatar" -> JsString(value))
            case None => Seq()
          })
        val toUserJson = Seq("id" -> JsNumber(toUser.id), "name" -> JsString(toUser.name), "login" -> JsString(toUser.login)) ++
          (toUser.avatar match {
            case Some(value) => Seq("avatar" -> JsString(value))
            case None => Seq()
          })
        val fields = Seq("id" -> JsNumber(message.id),
          "user" -> JsObject(fromUserJson),
          "toUser" -> JsObject(toUserJson),
          "date" -> JsNumber(message.date.getTime),
          "text" -> JsString(message.text))
        JsObject(fields)
      })))
    }
  }

  def addTopic() = Action.async(parse.json) { implicit request =>
    val userId = (request.body \ "user" \ "id").get.asInstanceOf[JsNumber].value.toLong
    val groupId = (request.body \ "groupId").get.asInstanceOf[JsNumber].value.toLong
    val text = (request.body \ "text").get.asInstanceOf[JsString].value
    val date = new Timestamp(Calendar.getInstance.getTime.getTime)
    topicsDAO.insert(Topic(groupId = groupId, userId = userId, date = date, text = text)).flatMap { case id =>
      usersDAO.findById(userId).map { case u =>
        val user = u.get
        Logger.debug(s"Adding topic: $userId, $groupId, $text")
        val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
          (user.avatar match {
            case Some(value) => Seq("avatar" -> JsString(value))
            case None => Seq()
          })
        mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("id" -> JsNumber(id),
          "group" -> JsObject(Seq("id" -> JsNumber(groupId))),
          "user" -> JsObject(userJson),
          "date" -> JsNumber(date.getTime),
          "text" -> JsString(text)))))
        Ok(Json.toJson(JsNumber(id)))
      }
    }
  }
}
