package api

import java.util

import actors.BotDescription
import api.{TextMessage, Behaviour, State, Bot}

import scala.util.matching.Regex

/**
  * Created by dsavvinov on 4/16/16.
  */
class TestClass extends BotDescription[collection.mutable.ListBuffer[String]] {
    def apply() = {
        val myBot = new Bot[collection.mutable.ListBuffer[String]]("my-bot", collection.mutable.ListBuffer[String]())

        val startState = State("Start")(new Behaviour[collection.mutable.ListBuffer[String]] {
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

        val echoState = State("Echo")(new Behaviour[collection.mutable.ListBuffer[String]] {
            override def handler(msg: TextMessage) = {
                val pattern = """find (\d*)""".r
                msg.text match {
                    case pattern(c) =>
                        say(
                            data().take(Integer.parseInt(c)).toString()
                        )
                        moveTo("Finish")
                    case other =>
                        data().append(other)
                }
            }
        })

        val talkFinishedState = State("Finish")(new Behaviour[collection.mutable.ListBuffer[String]] {
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