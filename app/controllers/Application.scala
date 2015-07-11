package controllers

import actors.WebSocketActor
import akka.actor.PoisonPill
import models.{Group, Comment, Topic, User}
import models.current._
import myUtils.MyPostgresDriver.simple._
import org.joda.time.DateTime
import play.api.{Logger, Play}
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

  def index = Action.async { implicit request =>
    request.cookies.get("user") match {
      case Some(cookie) =>
        DB.withSession { implicit session =>
          dao.users.filter(_.login === cookie.value).firstOption match {
            case Some(user) =>
              val webSocketUrl = routes.Application.webSocket(user.login).absoluteURL().replaceAll("http", "ws")
              Future.successful(Ok(views.html.index(user, getGroupsJsValue(user.id), webSocketUrl)))
            case None =>
              Future.successful(Redirect(Auth.getAuthUrl).discardingCookies(DiscardingCookie("user")))
          }
        }
      case None =>
        Future.successful(Redirect(Auth.getAuthUrl))
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

  def getGroupsJsValue(userId: Long) (): JsValue = {
    DB.withSession { implicit session =>
      val groups = dao.groups.list.map(_.id -> 0).toMap
      val groupTopics = dao.topics.filter(topic => topic.userId === userId)
        .groupBy(topic => topic.groupId)
        .map { case (groupId, g) => (groupId, g.map(_.groupId).countDistinct) }.list.map { case (groupId, count) =>
        groupId -> count
      }.toMap
      val groupComments = dao.comments.filter(comment => comment.userId === userId)
        .groupBy(comment => comment.groupId)
        .map { case (gId, g) => (gId, g.map(_.groupId).countDistinct) }.list.map { case (gId, c) =>
        gId -> c
      }.toMap
      val groupTotal = groups ++ (groupTopics ++ groupComments.map { case (gId, c) => gId -> (c + groupTopics.getOrElse(gId, 0)) })
      Json.toJson(JsObject(groupTotal.map { case (gId, count) =>
        gId -> JsNumber(count)
      }.toSeq.sortBy(- _._2.value)))
    }
  }

  def getGroups(userId: Long) = DBAction { implicit rs =>
    Ok(getGroupsJsValue(userId))
  }

  def getAllTopics(userId: Long) = getTopics(userId, None)

  def getGroupTopics(userId: Long, groupId: String) = getTopics(userId, Some(groupId))

  def getTopics(userId: Long, groupId: Option[String]) = DBAction { implicit rs =>
    val topics = (for {((topic, comment), user) <- dao.topics outerJoin dao.comments on { case (topic, comment) => comment.topicId === topic.id } leftJoin dao.users on { case ((topic, comment), user) => topic.userId === user.id }} yield (comment, topic, user)).filter { case (comment, topic, user) => groupId match {
      case Some(id) => topic.groupId === id
      case None => topic.groupId === topic.groupId
    }
    }.groupBy { case (comment, topic, user) => (topic.id, topic.date, topic.groupId, topic.text, user.id, user.name) }.map { case ((tId, tDate, gId, tText, uId, uName), g) => (tId, tDate, gId, tText, uId, uName, g.map(_._1.id).countDistinct, g.map(_._1.date).max) }.list.sortBy(columns => columns._8 match {
      case Some(maxCommentDate) => - maxCommentDate.getMillis
      case None => - columns._2.getMillis
    }).map { case (tId, tDate, gId, tText, uId, uName, c, d) => (tId, tDate, gId, tText, uId, uName) -> c }

    Ok(Json.toJson(topics.map { case ((tId, tDate, gId, tText, uId, uName), c) =>
      JsObject(Seq("topic" -> JsObject(Seq("id" -> JsNumber(tId), "date" -> Json.toJson(tDate), "groupId" -> JsString(gId), "text" -> JsString(tText), "user" -> JsObject(Seq("id" -> JsNumber(uId), "name" -> JsString(uName))))), "messages" -> JsNumber(c)))
    }))
  }

  def getMessages(userId: Long, topicId: Long) = DBAction { implicit rs =>
    val topic = (dao.topics.filter(_.id === topicId) leftJoin dao.users on { case (t, user) => t.userId === user.id }).first
    val comments = (dao.comments.filter(comment => comment.topicId === topicId) leftJoin dao.users on { case (comment, user) => comment.userId === user.id }).sortBy(_._1.date).list
    val messages = comments.+:(topic)
    Ok(Json.toJson(messages.map { case (message, user) =>
      val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
        (user.avatar match {
          case Some(value) => Seq("avatar" -> JsString(value))
          case None => Seq()
        })
      val fields = Seq("id" -> JsNumber(message.id),
        "groupId" -> JsString(message.groupId),
        "user" -> JsObject(userJson),
        "date" -> JsNumber(message.date.getMillis),
        "text" -> JsString(message.text)) ++ (message match {
        case c: Comment =>
          Seq("topicId" -> JsNumber(c.topicId))
        case _ => Seq()
      })
      JsObject(fields)
    }))
  }

  def addTopic = DBAction(parse.json) { implicit rs =>
    val userId = (rs.body \ "user" \ "id").asInstanceOf[JsNumber].value.toLong
    val groupId = (rs.body \ "groupId").asInstanceOf[JsString].value
    val text = (rs.body \ "text").asInstanceOf[JsString].value
    val date = new DateTime()
    val id = (dao.topics returning dao.topics.map(_.id)) += new Topic(groupId = groupId, userId = userId, date = date, text = text)
    Logger.debug(s"Submitted topic: $userId, $groupId, $text")
    val webSocketActor = Akka.system.actorSelection("/user/*.*")
    val user = dao.users.filter(_.id === userId).first
    val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
      (user.avatar match {
        case Some(value) => Seq("avatar" -> JsString(value))
        case None => Seq()
      })
    webSocketActor ! JsObject(Seq("id" -> JsNumber(id),
      "groupId" -> JsString(groupId),
      "user" -> JsObject(userJson),
      "date" -> JsNumber(date.getMillis),
      "text" -> JsString(text)))
    Ok(Json.toJson(JsNumber(id)))
  }

  def addComment = DBAction(parse.json) { implicit rs =>
    val userId = (rs.body \ "user" \ "id").asInstanceOf[JsNumber].value.toLong
    val groupId = (rs.body \ "groupId").asInstanceOf[JsString].value
    val topicId = (rs.body \ "topicId").asInstanceOf[JsNumber].value.toLong
    val text = (rs.body \ "text").asInstanceOf[JsString].value
    val date = new DateTime()
    val id = (dao.comments returning dao.comments.map(_.id)) += new Comment(groupId = groupId, userId = userId, topicId = topicId, date = date, text = text)
    Logger.debug(s"Submitted comment: $userId, $groupId, $topicId, $text")
    val webSocketActor = Akka.system.actorSelection("/user/*.*")
    val user = dao.users.filter(_.id === userId).first
    val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
      (user.avatar match {
        case Some(value) => Seq("avatar" -> JsString(value))
        case None => Seq()
      })
    webSocketActor ! JsObject(Seq("id" -> JsNumber(id),
      "groupId" -> JsString(groupId),
      "topicId" -> JsNumber(topicId),
      "user" -> JsObject(userJson),
      "date" -> JsNumber(date.getMillis),
      "text" -> JsString(text)))
    Ok(Json.toJson(JsNumber(id)))
  }

  def addGroup = DBAction(parse.json) { implicit rs =>
    val groupId = (rs.body).asInstanceOf[JsString].value
    dao.groups += new Group(groupId)
    Logger.debug(s"Adding group: $groupId")
    Ok
  }
}
