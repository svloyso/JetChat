import java.net.URI

import actors.ClusterEvent
import akka.actor.{Actor, ActorLogging, AddressFromURIString}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Subscribe
import mousio.etcd4j.EtcdClient
import play.api.Logger
import play.twirl.api.TemplateMagic.javaCollectionToScala
import play.api.Play.current
import play.api.libs.concurrent.Akka

class ClusterListener extends Actor with ActorLogging {
  val mediator = DistributedPubSubExtension(context.system).mediator

  val cluster = Cluster(context.system)

  val etcdPeers = System.getProperty("ETCDCTL_PEERS")
  if (etcdPeers != null) {
    Logger.debug("Connecting to etcd: " + etcdPeers)
    val addrs = etcdPeers.split(",").toList.map(addr => URI.create(if (addr.startsWith("http://")) addr else "http://" + addr))
    val client = new EtcdClient(addrs:_*)

    cluster.joinSeedNodes(client.get("/jetchat").recursive().send().get().node.nodes.toList.map { case node =>
      val ip = node.nodes.toList.find(_.key.endsWith("IP")).get.value
      val port = node.nodes.toList.find(_.key.endsWith("PORT")).get.value
      Logger.debug("A cluster seed discovered: " + ip + ":" + port)
      AddressFromURIString("akka.tcp://application@" + ip + ":" + port)
    })
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
      Logger.debug("Received a cluster event: " + event)
      Akka.system.actorSelection(s"/user/${event.userMask}.*") ! event.message
  }
}