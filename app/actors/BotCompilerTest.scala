package actors

import akka.actor._
import _root_.api.{DummyClass, Bot}
import play.api.Logger
import scala.reflect.runtime._
import scala.tools.reflect.ToolBox


/**
  * Created by dsavvinov on 4/9/16.
  */

object BotCompilerTest {
    //TODO: Debugging only! Remove ASAP
    val handler : String =
        """
      def receive : ActorRef => Actor.Receive = (sender : ActorRef) => {
              case TextMessage(senderId, groupId, topicId, text) =>
                   sender ! BotInternalOutcomingMessage(senderId, groupId, topicId,
                       s"Recieved message from $senderId in group $groupId and topic $topicId " + "\n" +
                           s"Message: $text")
          }
        """

    def createBot(system: ActorSystem, botCode: String, botName:String) = {
        Logger.info(s"Creating a bot with name $botName")

        val cm      = universe.runtimeMirror(getClass.getClassLoader)
        val tb      = cm.mkToolBox()
        val handlerClass = tb.eval(tb.parse(botCode)).asInstanceOf[Class[DummyClass]]
        val handlerObj = handlerClass.getConstructors()(0).newInstance()
        val handler = handlerObj.asInstanceOf[DummyClass].apply()
        system.actorOf(Props(classOf[Bot], system, botName, handler))
    }

    def apply(system: ActorSystem) = {
        createBot(system,
            """
                    import actors._
                    import akka.actor._
                    import scala.concurrent.duration.DurationInt
                    import scala.reflect.runtime
                    import scala.reflect.runtime._
                    import scala.reflect.runtime.universe._
                    import api.{DummyClass, TextMessage, BotInternalOutcomingMessage}

                    class RuntimeDummy extends DummyClass {

            """
                +
                handler
                +
                """
                    override def apply() = receive
                    }
                    scala.reflect.classTag[RuntimeDummy].runtimeClass
                """,
            "CompiledBot")
    }
}