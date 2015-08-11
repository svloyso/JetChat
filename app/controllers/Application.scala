package controllers

import actors.{ClusterEvent, WebSocketActor}
import akka.actor.PoisonPill
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import com.github.tototoshi.slick.MySQLJodaSupport._
import controllers.Auth._
import models.CustomDriver.simple._
import models._
import models.current._
import org.joda.time.DateTime
import play.api.Logger
import play.api.Play.current
import play.api.db.slick._
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.{Concurrent, Iteratee}
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Application extends Controller {
  implicit val userFormat = Json.format[User]
  implicit val topicFormat = Json.format[Topic]

  val TICK = JsString("Tick")
  val TACK = JsString("Tack")

  val mediator = DistributedPubSubExtension(Akka.system).mediator

  def index = Action.async { implicit request =>
    request.cookies.get("user") match {
      case Some(cookie) =>
        DB.withSession { implicit session =>
          dao.users.filter(_.login === cookie.value).firstOption match {
            case Some(user) =>
              val webSocketUrl = routes.Application.webSocket(user.login).absoluteURL().replaceAll("http", "ws")
              Future.successful(Ok(views.html.index(user, getUsersJsValue(user.id),
                getGroupsJsValue(user.id), webSocketUrl)))
            case None =>
              Future.successful(Redirect(Auth.getAuthUrl).discardingCookies(DiscardingCookie("user")))
          }
        }
      case None =>
        if (Auth.HUB_BASE_URL.nonEmpty) {
          Future.successful(Redirect(Auth.getAuthUrl))
        } else {
          DB.withSession { implicit session =>
            val user = dao.users.filter(_.login === HUB_MOCK_LOGIN).firstOption
            user match {
              case None =>
                dao.users += new User(login = HUB_MOCK_LOGIN, name = HUB_MOCK_NAME, avatar = Option(HUB_MOCK_AVATAR))
                val u = dao.users.filter(_.login === HUB_MOCK_LOGIN).first
                mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("newUser" -> JsObject(Seq("id" -> JsNumber(u.id), "name" -> JsString(u.name), "login" -> JsString(u.login)) ++
                  (u.avatar match {
                    case Some(value) => Seq("avatar" -> JsString(value))
                    case None => Seq()
                  }))))))
              case _ =>
            }
          }
          Future.successful(Redirect(controllers.routes.Application.index()).withCookies(Cookie("user", HUB_MOCK_LOGIN, httpOnly = false)))
        }
    }
  }

  def logout() = Action.async { implicit request =>
    Future.successful(Redirect(Auth.getLogoutUrl).discardingCookies(DiscardingCookie("user")))
  }

  var actorCounter = 0

  def webSocket(login: String) = WebSocket.using[JsValue] { request =>
    val (out, channel) = Concurrent.broadcast[JsValue]

    actorCounter += 1
    val actor = Akka.system.actorOf(WebSocketActor.props(channel), s"$login.$actorCounter")

    val in = Iteratee.foreach[JsValue] { message =>
      if (message.equals(TICK))
        channel.push(TACK)
    } map { _ =>
      actor ! PoisonPill
    }

    (in, out)
  }

  def getUser(login: String) = DBAction { implicit rs =>
    Ok(Json.toJson(dao.users.filter(_.login === login).firstOption))
  }

  def getUsersJsValue(userId: Long) (): JsValue = {
    DB.withSession { implicit session =>
      Json.toJson(dao.users.list)
    }
  }

  def getGroupsJsValue(userId: Long) (): JsValue = {
    DB.withSession { implicit session =>
      val groups = dao.groups.list.map(g => g.id -> (g.name, 0)).toMap

      val groupTopics = (dao.topics.filter(_.userId === userId) leftJoin dao.groups on { case (topic, group) => topic.groupId === group.id })
        .groupBy{ case (topic, group) => (group.id, group.name) }
        .map { case ((groupId, groupName), g) => (groupId, groupName, g.map(_._1.groupId).countDistinct) }.list
        .map { case (groupId, groupName, count) =>
          groupId -> (groupName, count)
        }.toMap

      val groupComments = (dao.comments.filter(_.userId === userId) leftJoin dao.groups on { case (comment, group) => comment.groupId === group.id })
        .groupBy{ case (comment, group) => (group.id, group.name) }
        .map { case ((groupId, groupName), g) => (groupId, groupName, g.map(_._1.groupId).countDistinct) }.list.map {
        case (groupId, groupName, c) => groupId -> (groupName, c)
      }.toMap

      val groupTotal = groups ++ (groupTopics ++ groupComments.map { case (groupId, groupCommentToken) =>
        val groupTopicToken = groupTopics.getOrElse(groupId, (groupCommentToken._1, 0))
        groupId -> (groupCommentToken._1 -> (groupCommentToken._2 + groupTopicToken._2))
      })

      Json.toJson(JsArray(groupTotal.toSeq.sortBy(a => a._2._2).map { case (groupId, token) =>
        JsObject(Seq("id" -> JsNumber(groupId), "name" -> JsString(token._1), "count" -> JsNumber(token
          ._2)))
      }))
    }
  }

  def getGroups(userId: Long) = DBAction { implicit rs =>
    Ok(getGroupsJsValue(userId))
  }

  def getAllTopics(userId: Long) = getTopics(userId, None)

  def getGroupTopics(userId: Long, groupId: Long) = getTopics(userId, Some(groupId))

  def getTopics(userId: Long, groupId: Option[Long]) = DBAction { implicit rs =>
    val userTopics = (dao.topics leftJoin dao.users on { case (topic, user) => topic.userId === user.id } leftJoin dao
      .groups on { case ((topic, user), group) => topic.groupId === group.id }).filter { case ((topic, user), group) => groupId match {
      case Some(id) => topic.groupId === id
      case None => topic.groupId === topic.groupId
    }}.sortBy(_._1._1.date desc).map {
      case ((topic, user), group) =>
        (topic.id, topic.date, topic.text, group.id, group.name, user.id, user.name) -> 0}.list.toMap

    val commentedTopics = (dao.comments leftJoin dao.topics on { case (comment, topic) =>
      comment.topicId === topic.id
    } leftJoin dao.users on { case ((comment, topic), user) => topic.userId === user.id
    } leftJoin dao.groups on { case (((comment, topic), user), group) => topic.groupId === group.id }).filter { case (((comment, topic), user), group) => groupId match {
      case Some(id) => topic.groupId === id
      case None => topic.groupId === topic.groupId
    }}.groupBy { case (((comment, topic), user), group) =>
      (topic.id, topic.date, topic.text, group.id, group.name, user.id, user.name)
    }.map { case ((topicId, topicDate, topicText, gId, groupName, uId, userName), g) =>
      (topicId, topicDate, topicText, gId, groupName, uId, userName, g.map(_._1._1._1.id).countDistinct, g.map(_._1._1._1.date).max) }
      .sortBy(_._9 desc).list.map { case (topicId, topicDate, topicText, gId, groupName, uId, userName, c, d) =>
      (topicId, topicDate, topicText, gId, groupName, uId, userName) -> c }.toMap

    val total = (userTopics ++ commentedTopics).toSeq.sortBy(- _._1._2.getMillis)
    Ok(Json.toJson(JsArray(total.map { case ((topicId, topicDate, topicText, gId, groupName, uId, userName), c) =>
      JsObject(Seq("topic" -> JsObject(Seq("id" -> JsNumber(topicId), "date" -> Json.toJson(topicDate), "group" -> JsObject
      (Seq("id" -> JsNumber(gId), "name" -> JsString(groupName))),
        "text" -> JsString(topicText), "user" -> JsObject(Seq("id" -> JsNumber(uId), "name" -> JsString(userName))))), "messages" -> JsNumber(c)))
    })))
  }

  def getMessages(userId: Long, topicId: Long) = DBAction { implicit rs =>
    val topic = (dao.topics.filter(_.id === topicId) leftJoin dao.users on { case (topic, user) => topic.userId === user.id } leftJoin dao.groups on { case ((topic, user), group) => topic.groupId === group.id }).map { case ((topic, user), group) => (topic, user, group)}.first

    val comments = (dao.comments.filter(comment => comment.topicId === topicId) leftJoin dao.users on { case
      (comment, user) => comment.userId === user.id } leftJoin dao.groups on { case ((c, user), group) => c.groupId === group.id }).map { case ((comment, user), group)
    => (comment, user, group)}.sortBy(_._1.date).list
    val messages = comments.+:(topic)
    Ok(Json.toJson(JsArray(messages.map { case (message, user, group) =>
      val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
        (user.avatar match {
          case Some(value) => Seq("avatar" -> JsString(value))
          case None => Seq()
        })
      val fields = Seq("id" -> JsNumber(message.id),
        "group" -> JsObject(Seq("id" -> JsNumber(group.id), "name" -> JsString(group.name))),
        "user" -> JsObject(userJson),
        "date" -> JsNumber(message.date.getMillis),
        "text" -> JsString(message.text)) ++ (message match {
        case c: Comment =>
          Seq("topicId" -> JsNumber(c.topicId))
        case _ => Seq()
      })
      JsObject(fields)
    })))
  }

  def getDirectMessages(fromUserId: Long, toUserId: Long) = DBAction { implicit rs =>
    val messages = (for { ((dm, fromUser), toUser) <- dao.directMessages.filter(dm => (dm.fromUserId === fromUserId && dm.toUserId === toUserId) ||
      (dm.fromUserId === toUserId && dm.toUserId === fromUserId)) leftJoin dao.users on { case (dm, fromUser) => dm.fromUserId === fromUser.id } leftJoin dao.users on { case ((dm, fromUser), toUser) => dm.toUserId === toUser.id }} yield (dm, fromUser, toUser)).sortBy(_._1.date).list
    Ok(Json.toJson(JsArray(messages.map { case (dm, fromUser, toUser) =>
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
      val fields = Seq("id" -> JsNumber(dm.id),
        "user" -> JsObject(fromUserJson),
        "toUser" -> JsObject(toUserJson),
        "date" -> JsNumber(dm.date.getMillis),
        "text" -> JsString(dm.text))
      JsObject(fields)
    })))
  }

  def addTopic() = DBAction(parse.json) { implicit rs =>
    val userId = (rs.body \ "user" \ "id").asInstanceOf[JsNumber].value.toLong
    val groupId = (rs.body \ "groupId").asInstanceOf[JsNumber].value.toLong
    val text = (rs.body \ "text").asInstanceOf[JsString].value
    val date = new DateTime()
    val id = (dao.topics returning dao.topics.map(_.id)) += new Topic(groupId = groupId, userId = userId, date = date, text = text)
    Logger.debug(s"Adding topic: $userId, $groupId, $text")
    val user = dao.users.filter(_.id === userId).first
    val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
      (user.avatar match {
        case Some(value) => Seq("avatar" -> JsString(value))
        case None => Seq()
      })
    mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("id" -> JsNumber(id),
      "group" -> JsObject(Seq("id" -> JsNumber(groupId))),
      "user" -> JsObject(userJson),
      "date" -> JsNumber(date.getMillis),
      "text" -> JsString(text)))))
    Ok(Json.toJson(JsNumber(id)))
  }

  def addComment() = DBAction(parse.json) { implicit rs =>
    val userId = (rs.body \ "user" \ "id").asInstanceOf[JsNumber].value.toLong
    val groupId = (rs.body \ "groupId").asInstanceOf[JsNumber].value.toLong
    val topicId = (rs.body \ "topicId").asInstanceOf[JsNumber].value.toLong
    val text = (rs.body \ "text").asInstanceOf[JsString].value
    val date = new DateTime()
    val id = (dao.comments returning dao.comments.map(_.id)) += new Comment(groupId = groupId, userId = userId, topicId = topicId, date = date, text = text)
    Logger.debug(s"Adding comment: $userId, $groupId, $topicId, $text")
    val user = dao.users.filter(_.id === userId).first
    val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
      (user.avatar match {
        case Some(value) => Seq("avatar" -> JsString(value))
        case None => Seq()
      })
    mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("id" -> JsNumber(id),
      "groupId" -> JsNumber(groupId),
      "topicId" -> JsNumber(topicId),
      "user" -> JsObject(userJson),
      "date" -> JsNumber(date.getMillis),
      "text" -> JsString(text)))))
    Ok(Json.toJson(JsNumber(id)))
  }

  def addDirectMessage() = DBAction(parse.json) { implicit rs =>
    val fromUserId = (rs.body \ "user" \ "id").asInstanceOf[JsNumber].value.toLong
    val toUserId = (rs.body \ "toUser" \ "id").asInstanceOf[JsNumber].value.toLong
    val text = (rs.body \ "text").asInstanceOf[JsString].value
    val date = new DateTime()
    val id = (dao.directMessages returning dao.directMessages.map(_.id)) += new DirectMessage(fromUserId = fromUserId, toUserId = toUserId, date = date, text = text)
    Logger.debug(s"Adding direct message: $fromUserId, $toUserId, $text")
    val user = dao.users.filter(_.id === fromUserId).first
    val toUser = dao.users.filter(_.id === toUserId).first
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
      "date" -> JsNumber(date.getMillis),
      "text" -> JsString(text)))
    mediator ! Publish("cluster-events", ClusterEvent(user.login, message))
    mediator ! Publish("cluster-events", ClusterEvent(toUser.login, message))
    Ok(Json.toJson(JsNumber(id)))
  }

  def addGroup() = DBAction(parse.json) { implicit rs =>
    val groupName = rs.body.asInstanceOf[JsString].value
    val id = (dao.groups returning dao.groups.map(_.id)) += new Group(name = groupName)
    Logger.debug(s"Adding group: $groupName")
    val groupJson = JsObject(Seq("id" -> JsNumber(id), "name" -> JsString(groupName)))
    mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("newGroup" -> groupJson))))
    Ok(Json.toJson(groupJson))
  }
}
