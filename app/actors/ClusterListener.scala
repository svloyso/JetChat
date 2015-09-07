package actors

import java.net.URI
import java.util.Calendar

import akka.actor.{AddressFromURIString, Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import mousio.etcd4j.EtcdClient
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.twirl.api.TemplateMagic.javaCollectionToScala

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ClusterListener extends Actor with ActorLogging {
  lazy val discoverInterval = current.configuration.getLong("cluster.seed-nodes.discover-interval").getOrElse(System.getProperty("cluster.seed-nodes.discover-interval", "60000").toLong)
  lazy val disconnectTimeout = current.configuration.getLong("cluster.seed-nodes.disconnect-timeout").getOrElse(System.getProperty("cluster.seed-nodes.disconnect-timeout", "600000").toLong)

  import DistributedPubSubMediator.Subscribe

  val mediator = DistributedPubSub(context.system).mediator

  val cluster = Cluster(context.system)

  val etcdPeers = System.getProperty("ETCDCTL_PEERS")

  val addrs = etcdPeers.split(",").toList.map(addr => URI.create(if (addr.startsWith("http://")) addr else "http://" + addr))
  val client = new EtcdClient(addrs:_*)

  val seeds = new collection.mutable.HashSet[String]()

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
    mediator ! Subscribe("cluster-events", self)

    if (etcdPeers != null) {
      Logger.debug("Discovering seeds with ETCD: " + etcdPeers)

      Akka.system.scheduler.schedule(0 seconds, discoverInterval millisecond, self, DiscoveryEvent())
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
    case event: ClusterEvent =>
      Logger.debug("Received a cluster event: " + event)
      Akka.system.actorSelection(s"/user/${event.userMask}.*") ! event.message
    case event:DiscoveryEvent =>
      val selfHost = cluster.selfAddress.host.get
      val selfPort = cluster.selfAddress.port.get

      Logger.debug(s"Updating cluster seed information: '$selfHost:$selfPort'")
      client.put(s"/jetchat/$selfHost:$selfPort", Calendar.getInstance.getTime.getTime.toString).send().get()

      val seedInfoNodes = client.get("/jetchat").send().get.node.nodes.toList
      seedInfoNodes.map { case seedInfo =>
        if (seedInfo.key.contains(":")) {
          val seedAddress = seedInfo.key.substring(9)
          if (!seeds.contains(seedAddress)) {
            try {
              if (Calendar.getInstance().getTime.getTime - seedInfo.value.toLong < disconnectTimeout) {
                // 10 minutes
                seeds.add(seedAddress)
                Logger.debug(s"A cluster seed info discovered: '$seedAddress'")
              } else {
                Logger.debug(s"Removing out-dated cluster seed: '$seedAddress'")
                client.delete(seedInfo.key).recursive().send()
              }
            } catch {
              case ignore: Throwable =>
                Logger.debug(s"Removing unparsable cluster seed info: '${seedInfo.key}'")
                client.delete(seedInfo.key).recursive().send()
            }
          }
        } else {
          Logger.debug(s"Removing unparsable cluster seed info: '${seedInfo.key}'")
          client.delete(seedInfo.key).recursive().send()
        }
      }

      cluster.joinSeedNodes(seeds.map(address => AddressFromURIString(s"akka.tcp://application@$address")).toList)
  case _: MemberEvent => // ignore
  }
}