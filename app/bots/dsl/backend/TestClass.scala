package bots.dsl.backend

import bots.dsl.backend.BotMessages.{ChatMessage, TextMessage}
import bots.dsl.frontend.{GlobalBehaviour, Behaviour, Bot, State}

import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * Created by dsavvinov on 4/16/16.
  */

class TestClass extends BotDescription {
  def apply() = {
    import scala.collection.mutable.ListBuffer
    case class RegisterUser(userID: Long, group: String)
    case class ScheduleBroadcast(group: String, time: FiniteDuration, text: String)

    val bot = new Bot("scheduler-bot")

    type MapType = collection.mutable.Map[String, collection.mutable.ListBuffer[Long]]
    bot storesGlobal[MapType] "map" initWith collection.mutable.Map.empty[String, collection.mutable.ListBuffer[Long]]

    val pattern_after = """register "([^"]*)" after <([^>]*)> for group (.*)""".r
    val pattern_subscribe = """subscribe as (.*)""".r

    val s = State("Listening")(new Behaviour {
      /** user-defined function for handling incoming messages **/
      override def handler = {
          case TextMessage(uid, gid, tip, text) => {
            text match {
              case pattern_after(messageToSend, time, group) => {
                val duration = Duration(time).asInstanceOf[FiniteDuration]
                sendToGlobal(ScheduleBroadcast(group, duration, messageToSend))
              }
              case pattern_subscribe(group) => {
                sendToGlobal(RegisterUser(getUserID, group))
              }
              case other =>
            }
          }
        }
    })

    bot overrideGlobal new GlobalBehaviour {
      onMessageReceive {
        case RegisterUser(id, group) =>
          val map = globalStorage("map").asInstanceOf[MapType]
          map.get(group) match {
            case Some(set) => map += (group -> (set += id))
            case None => {
              val lb = new ListBuffer[Long]
              lb += id
              map += (group -> lb)
            }
          }
        case ScheduleBroadcast(group, time, text) =>
          val task: (Unit => Any) = Unit => {
            val map = globalStorage("map").asInstanceOf[MapType]
            map.get(group) match {
              case Some(set) => {
                set foreach { userId => sendTo(userId, text) }
              }
              case None =>
            }
          }
          schedule(task, time)
      }
    }

    bot startWith s

    bot + s
  }
}