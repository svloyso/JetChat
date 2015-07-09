package controllers

import actors.WebSocketActor
import akka.actor.PoisonPill
import models.{Topic, User}
import models.current._
import myUtils.MyPostgresDriver.simple._
import org.joda.time.DateTime
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
    val webSocketUrl = routes.Application.webSocket("guest").absoluteURL().replaceAll("https://", "wss://").
      replaceAll("http://", "ws://")
    Future.successful(Ok(views.html.index(webSocketUrl)))
  }

  var actorCounter = 0

  def webSocket(email: String) = WebSocket.using[JsValue] { request =>
    val (out, channel) = Concurrent.broadcast[JsValue]

    actorCounter += 1
    val actor = Akka.system.actorOf(WebSocketActor.props(channel), s"$email.$actorCounter")

    val in = Iteratee.foreach[JsValue] { message =>
      if (message.equals(TICK))
        channel.push(TACK)
    } map { _ =>
      actor ! PoisonPill
    }

    (in, out)
  }

  def getUser(email: String) = DBAction { implicit rs =>
    Ok(Json.toJson(dao.users.filter(_.email === email).firstOption))
  }

  def getRecentGroups(userId: Long) = DBAction { implicit rs =>
    val now = new DateTime()
    val groups = dao.groups.list.map(_.id -> 0)
    val groupTopics = dao.topics.filter(m => m.userId === userId && m.date > now.minusDays(7))
      .groupBy(m => m.groupId)
      .map { case (gId, g) => (gId, g.map(_.groupId).countDistinct) }.list.map { case (gId, c) =>
      gId -> c
    }.toMap
    val groupMessages = dao.messages.filter(m => m.userId === userId && m.date > now.minusDays(7))
      .groupBy(m => m.groupId)
      .map { case (gId, g) => (gId, g.map(_.groupId).countDistinct) }.list.map { case (gId, c) =>
      gId -> c
    }.toMap
    val groupTotal = groups ++ (groupTopics ++ groupMessages.map { case (gId, c) => gId -> (c + groupTopics.getOrElse(gId, 0)) })

    Ok(Json.toJson(JsObject(groupTotal.map { case (gId, count) =>
      gId -> JsNumber(count)
    }.toSeq.sortBy(_._2.value))))
  }

  def getAllRecentTopics(userId: Long) = getRecentTopics(userId, None)

  def getGroupRecentTopics(userId: Long, groupId: String) = getRecentTopics(userId, Some(groupId))

  def getRecentTopics(userId: Long, groupId: Option[String]) = DBAction { implicit rs =>
    val topics = (for {((m, t), u) <- dao.messages leftJoin dao.topics on { case (m, t) => m.topicId === t.id } leftJoin dao.users on { case ((m, t), u) => t.userId === u.id }} yield (m, t, u)).filter { case (m, t, u) => groupId match {
      case Some(gId) => t.groupId === gId
      case None => t.groupId === t.groupId
    }
    }.groupBy { case (m, t, u) => (t.id, t.date, t.text, u.id, u.name) }.map { case ((tId, tDate, tText, uId, uName), g) => (tId, tDate, tText, uId, uName, g.map(_._1.id).countDistinct, g.map(_._1.date).max) }.sortBy(_._7.desc).list.map { case (tId, tDate, tText, uId, uName, c, d) => (tId, tDate, tText, uId, uName) -> c }

    Ok(Json.toJson(topics.map { case ((tId, tDate, tText, uId, uName), c) =>
      JsObject(Seq("topic" -> JsObject(Seq("id" -> JsNumber(tId), "date" -> Json.toJson(tDate), "text" -> JsString(tText), "userId" -> JsNumber(uId), "userName" -> JsString(uName))), "messages" -> JsNumber(c)))
    }))
  }
}
