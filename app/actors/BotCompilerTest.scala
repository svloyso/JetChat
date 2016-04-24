package actors

import akka.actor._
import _root_.api.{Bot}
import play.api.Logger
import scala.reflect.runtime._
import scala.tools.reflect.ToolBox


abstract class BotDescription[T] {
    def apply() : Bot[T]
}
/**
  * Created by dsavvinov on 4/9/16.
  */

object BotCompilerTest {
    //TODO: Debugging only! Remove ASAP
    val code : String =
        """
          class MyBotDescription extends BotDescription[List[String]] {
              def apply() = {
                  val myBot = new Bot[List[String]]("my-bot", List[String]())

                  val startState = State("Start")(new Behaviour[List[String]] {
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

                  val echoState = State("Echo")(new Behaviour[List[String]] {
                      override def handler(msg: TextMessage) = {
                          msg.text match {
                              case "bye" =>
                                  say("Good bye!")
                                  moveTo("Finish")
                              case other =>
                                  say(other)
                          }
                      }
                  })

                  val talkFinishedState = State("Finish")(new Behaviour[List[String]] {
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
        """

    def createBot(system: ActorSystem, botCode: String, botName:String) = {
        Logger.info(s"Creating a bot with name $botName")

        val cm      = universe.runtimeMirror(getClass.getClassLoader)
        val tb      = cm.mkToolBox()
        val botUserDefinedClass = tb.eval(tb.parse(botCode)).asInstanceOf[Class[BotDescription[List[String]]]]
        val botUserDefinedObj = botUserDefinedClass.getConstructors()(0).newInstance()
        val bot = botUserDefinedObj.asInstanceOf[BotDescription[List[String]]].apply()
        bot.launch(system)
    }

    def apply(system: ActorSystem) = {
        createBot(system,
            """
                   import api.{TextMessage, Behaviour, State, Bot}
                   import actors.BotDescription
                   import java.util

            """
                +
                code
                +
                """
                    scala.reflect.classTag[MyBotDescription].runtimeClass
                """,
            "CompiledBot")
    }
}