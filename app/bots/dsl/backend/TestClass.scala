package bots.dsl.backend

import bots.dsl.backend.BotMessages.TextMessage
import bots.dsl.frontend.{Behaviour, Bot, State}

/**
  * Created by dsavvinov on 4/16/16.
  */

class TestClass extends BotDescription {
    def apply() = {
        import scala.collection.mutable.ListBuffer
        val myBot = new Bot("my-bot")
        myBot.storesData[collection.mutable.ListBuffer[String]]("history", new ListBuffer[String]())

        val startState = State("Start")(new Behaviour {
            override def handler(msg: TextMessage) = {
                msg.text match {
                    case "hello" =>
                        say("hello")
                        moveTo("Echo")
                    case other =>
                        say("I don't want to talk with impolite ones")
                }
            }
        })

        val echoState = State("Echo")(new Behaviour {
            override def handler(msg: TextMessage) = {
                val pattern = """find (\d*)""".r
                msg.text match {
                    case pattern(c) =>
                        say(
                            data.history.asInstanceOf[collection.mutable.ListBuffer[String]].
                                take(Integer.parseInt(c)).toString()
                        )
                        moveTo("Finish")
                    case other =>
                        data.history.asInstanceOf[collection.mutable.ListBuffer[String]].
                            append(other)
                }
            }
        })

        val talkFinishedState = State("Finish")(new Behaviour {
            override def handler(msg: TextMessage): Unit = {
                msg.text match {
                    case "sorry" =>
                        say("OK, let's talk a bit more")
                        moveTo("Echo")
                    case other =>
                        say("Talk is finished. Say \"sorry\" to start it again")
                }
            }
        })

        myBot startWith startState

        myBot + startState + echoState + talkFinishedState
    }
}