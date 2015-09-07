package actors

import java.net.URI

import akka.actor.{Actor, ActorLogging, AddressFromURIString}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import mousio.etcd4j.EtcdClient
import mousio.etcd4j.responses.EtcdException
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.twirl.api.TemplateMagic.javaCollectionToScala

class ClusterListener extends Actor with ActorLogging {
  import DistributedPubSubMediator.{ Subscribe, SubscribeAck }

  val mediator = DistributedPubSub(context.system).mediator

  val cluster = Cluster(context.system)

  val etcdPeers = System.getProperty("ETCDCTL_PEERS")

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
    mediator ! Subscribe("cluster-events", self)

    if (etcdPeers != null) {
      Logger.debug("Connecting to etcd: " + etcdPeers)
      val addrs = etcdPeers.split(",").toList.map(addr => URI.create(if (addr.startsWith("http://")) addr else "http://" + addr))
      val client = new EtcdClient(addrs:_*)

      val root = try {
        Some(client.get("/jetchat").recursive().send().get())
      } catch {
        case e: EtcdException =>
          None
      }
      if (root.isDefined) {
        cluster.joinSeedNodes(root.get.node.nodes.toList.map { case node =>
          val ip = node.nodes.toList.find(_.key.endsWith("IP")).get.value
          val port = node.nodes.toList.find(_.key.endsWith("PORT")).get.value
          Logger.debug("A cluster seed discovered: " + ip + ":" + port)
          AddressFromURIString("akka.tcp://application@" + ip + ":" + port)
        })
      }
    }
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
      Logger.debug("Received a cluster event: " + event)
      Akka.system.actorSelection(s"/user/${event.userMask}.*") ! event.message
  }
}