import actors.ClusterEvent
import akka.actor.{Actor, ActorLogging, AddressFromURIString}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Subscribe
import play.api.Play.current
import play.api.libs.concurrent.Akka

import scala.collection.mutable.ListBuffer

class ClusterListener extends Actor with ActorLogging {
  val mediator = DistributedPubSubExtension(context.system).mediator

  val cluster = {
    val seeds = new ListBuffer[String]()
    try {
      for (i <- 0 to 100) {
        val seed = System.getProperty(s"akka.seed.$i")
        if (seed != null) {
          seeds += seed
        } else {
          throw new Exception()
        }
      }
    } catch {
      case _: Throwable => // ...
    }
    val cluster = Cluster(context.system)
    cluster.joinSeedNodes(seeds.toList.map(AddressFromURIString(_)))
    cluster
  }

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
    mediator ! Subscribe("cluster-events", self)
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}",
        member.address, previousStatus)
    case _: MemberEvent => // ignore
    case event: ClusterEvent =>
      Akka.system.actorSelection(s"/user/${event.userMask}.*") ! event.message
  }
}