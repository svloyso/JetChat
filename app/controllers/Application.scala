package controllers

import actors.WebSocketActor
import akka.actor.PoisonPill
import models.User
import models.current._
import myUtils.MyPostgresDriver.simple._
import play.api.Play.current
import play.api.db.slick._
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.{Concurrent, Iteratee}
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Application extends Controller {
  implicit val userFormat = Json.format[User]

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
    val json = Json.toJson(dao.users.filter(_.email === email).firstOption)
    Ok(json)
  }

  def ping() = Action { implicit rs =>
    Ok("pong")
  }
}
