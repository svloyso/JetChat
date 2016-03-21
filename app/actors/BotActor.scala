package actors

import actors._
import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.typesafe.config.ConfigRenderOptions
import java.sql.Timestamp
import java.util.Calendar
import java.util.concurrent.TimeoutException
import javax.inject.{Inject, Singleton}
import models._
import models.api.IntegrationTokensDAO
import play.api.libs.functional.syntax._
import play.api.libs.iteratee.{Concurrent, Iteratee}
import play.api.libs.json._
import play.api.mvc._
import play.api.{Play, Logger}
import _root_.api.{CollectedMessages, Integration, TopicComment}
import _root_.api.Integration
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}



class BotActor(system: ActorSystem,
               commentsDAO: CommentsDAO) extends Actor with ActorLogging {

  var isMaster = false

  val mediator = DistributedPubSub(system).mediator

  override def preStart(): Unit = {
    system.eventStream.subscribe(self, classOf[MasterEvent])
    system.eventStream.subscribe(self, classOf[SlaveEvent])

    system.eventStream.publish(MasterStateInquiry())


  }

  override def receive: Receive = {
    case masterEvent: MasterEvent =>
      if (!isMaster) {
        self ! ReceiveMessagesEvent
      }
      isMaster = true
      log.info("Turning master")
    case slaveEvent: SlaveEvent =>
      isMaster = true
      log.info("Turning slave")
    case MentionEvent(myId, groupId, topicId, text) =>
      log.info(s"Bot ($myId) was mentioned in message $text")
      val date = new Timestamp(Calendar.getInstance.getTime.getTime)
      val echoText = "And you said : \"" ++ text ++ "\""
      commentsDAO.insert(Comment(groupId = groupId, userId = myId, topicId = topicId, date = date, text = echoText)).map { case id =>
        val userJson = Seq("id" -> JsNumber(myId), "name" -> JsString("Bot"), "login" -> JsString("Bot")) ++ Seq()
        mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("id" -> JsNumber(id),
          "group" -> JsObject(Seq("id" -> JsNumber(groupId))),
          "topicId" -> JsNumber(topicId),
          "user" -> JsObject(userJson),
          "date" -> JsNumber(date.getTime),
          "text" -> JsString(echoText)))))
      }
  }
}

object BotActor {
  val DEFAULT_DURATION = 30.seconds

  def actorOf(system: ActorSystem,
              commentsDAO: CommentsDAO): ActorRef =
    system.actorOf(Props(new BotActor(system, commentsDAO)),
      "BotActor")

  def actorSelection(system: ActorSystem): ActorSelection =
    system.actorSelection("/user/BotActor")
}

case class MentionEvent(userId: Long, integrationGroupId: Long, integrationTopicId: Long, text: String)