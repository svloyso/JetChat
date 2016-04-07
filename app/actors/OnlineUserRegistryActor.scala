package actors

import akka.actor.ActorLogging
import akka.cluster.Cluster
import akka.cluster.ddata._
import akka.cluster.ddata.Replicator._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import play.api.libs.json.{JsNumber, JsObject}

import scala.concurrent.duration._

case class Tick(userId: Long)
case object Refresh
case class UserKey(userId:Long) extends Key[LWWRegister[Long]](userId.toString)

class OnlineUserRegistryActor(registry: OnlineUserRegistry) extends MasterActor with ActorLogging {
  val GO_OFFLINE = 30.seconds
  val REFRESH = 10.seconds

  val replicator = DistributedData(context.system).replicator
  val mediator = DistributedPubSub(context.system).mediator

  implicit val node = Cluster(context.system)

  val UserKeys = ORSetKey[Long]("online-user-keys")

  replicator ! Subscribe(UserKeys, self)

  import context.dispatcher
  val refreshTask = context.system.scheduler.schedule(REFRESH, REFRESH, self, Refresh)

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, MasterEvent.getClass)
    context.system.eventStream.subscribe(self, SlaveEvent.getClass)
    context.system.eventStream.publish(MasterStateInquiry())
  }

  override def receiveAsMaster: Receive = {
    case Tick(userId) =>
      replicator ! Update(UserKey(userId), WriteLocal, None)(_ => LWWRegister(System.currentTimeMillis()))
      if (!registry.userKeys.contains(userId)) {
        replicator ! Update(UserKeys, ORSet.empty[Long], WriteLocal)(_ + userId)
        mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("userOnline" -> JsNumber(userId)))))
      }

    case Refresh =>
      if (registry.userKeys.nonEmpty) {
        registry.userKeys.foreach(userId => replicator ! Get(UserKey(userId), ReadLocal))
      }

    case g @ GetSuccess(UserKey(userId), req) =>
      if (System.currentTimeMillis() - g.get(UserKey(userId)).value > GO_OFFLINE.toMillis) {
        replicator ! Update(UserKeys, ORSet.empty[Long], WriteLocal)(_ - userId)
        mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("userOffline" -> JsNumber(userId)))))
      }

    case c @ Changed(UserKeys) =>
      val userKeys = c.get(UserKeys).elements
      registry.userKeys = userKeys
      log.info("Online users: {}", userKeys)
    case _ =>
  }

  override def receiveAsSlave: Receive = {
    case Tick(userId) =>
      replicator ! Update(UserKey(userId), WriteLocal, None)(_ => LWWRegister(System.currentTimeMillis()))
      if (!registry.userKeys.contains(userId)) {
        replicator ! Update(UserKeys, ORSet.empty[Long], WriteLocal)(_ + userId)
        mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("userOnline" -> JsNumber(userId)))))
      }

    case c @ Changed(UserKeys) =>
      val userKeys = c.get(UserKeys).elements
      registry.userKeys = userKeys
      log.info("Online users: {}", userKeys)
    case _ =>
  }

  override def postStop(): Unit = refreshTask.cancel()
}