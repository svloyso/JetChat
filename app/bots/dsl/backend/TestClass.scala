package bots.dsl.backend

import bots.dsl.backend.BotMessages.TextMessage
import bots.dsl.frontend.{Behaviour, Bot, State}

import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * Created by dsavvinov on 4/16/16.
  */

class TestClass extends BotDescription {
  def apply() = {

    val bot = new Bot("scheduler-bot")

    val pattern_after = """register "([^"]*)" after (.*)""".r
    val s = State("Listening")(new Behaviour {
      /** user-defined function for handling incoming messages **/
      override def handler(msg: TextMessage): Unit = {
        msg.text match {
          case pattern_after(messageToSend, time) => {
            schedule(Unit => broadcast(messageToSend), Duration(time).asInstanceOf[FiniteDuration])
          }
          case other =>
        }
      }
    })

    bot startWith s

    bot + s
  }
}