package actors

import akka.actor.{Actor, Props}
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.JsValue

object WebSocketActor {
  def props(channel: Concurrent.Channel[JsValue]):Props = Props(new WebSocketActor(channel))
}

class WebSocketActor(channel: Concurrent.Channel[JsValue]) extends Actor {
  def receive = {
    case x: JsValue => channel.push(x)
  }
}