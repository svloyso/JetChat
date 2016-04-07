package actors

import java.net.URI

import akka.actor.{Actor, ActorLogging, AddressFromURIString}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import mousio.etcd4j.EtcdClient
import mousio.etcd4j.responses.EtcdException
import play.api.Application
import play.twirl.api.TemplateMagic.javaCollectionToScala

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ClusterListener(application: Application) extends Actor with ActorLogging {
  val NODES_DISCOVER_INTERVAL = application.configuration.getLong("cluster.seed-nodes.discover-interval").getOrElse(System.getProperty("cluster.seed-nodes.discover-interval", "6000").toLong)
  val NODES_TTL = application.configuration.getLong("cluster.seed-nodes.ttl").getOrElse(System.getProperty("cluster.seed-nodes.ttl", "12000").toLong)
  val MASTER_TTL = application.configuration.getLong("cluster.seed-nodes.master-ttl").getOrElse(System.getProperty("cluster.seed-nodes.master-ttl", "24000").toLong)

  import DistributedPubSubMediator.Subscribe

  val mediator = DistributedPubSub(context.system).mediator

  val cluster = Cluster(context.system)

  val etcdPeers = System.getProperty("ETCDCTL_PEERS")

  val client = if (etcdPeers != null) {
    val addrs = etcdPeers.split(",").toList.map(addr => URI.create(if (addr.startsWith("http://")) addr else "http://" + addr))
    new EtcdClient(addrs: _*)
  } else {
    null
  }

  var seeds = new collection.mutable.HashSet[String]()
  var isMaster = client == null

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
    mediator ! Subscribe("cluster-events", self)
    context.system.eventStream.subscribe(self, classOf[MasterStateInquiry])

    if (client != null) {
      context.system.scheduler.schedule(0.seconds, NODES_DISCOVER_INTERVAL.millisecond, self, DiscoveryEvent())
    }
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case inquiry: MasterStateInquiry =>
      context.system.eventStream.publish(if (isMaster) MasterEvent else SlaveEvent )
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}",
        member.address, previousStatus)
    case event: ClusterEvent =>
      log.info("Received a cluster event: " + event)
      context.system.actorSelection(s"/user/${event.userMask}.*") ! event.message
    case event: DiscoveryEvent =>
      val selfHost = cluster.selfAddress.host.get
      val selfPort = cluster.selfAddress.port.get

      log.info(s"Updating cluster seed information: '$selfHost:$selfPort'")
      client.put(s"/jetchat/seeds/$selfHost:$selfPort", "up").ttl((NODES_TTL / 1000).toInt).send().get()

      val newSeeds = new collection.mutable.HashSet[String]()
      val seedInfoNodes = client.get("/jetchat/seeds").send().get.node.nodes.toList
      seedInfoNodes.foreach { case i =>
        if (i.key.contains(":")) {
          val address = i.key.substring(15)
          newSeeds.add(address)
          if (!seeds.contains(address)) {
            log.info(s"A cluster seed found: '$address'")
          }
        }
      }
      if (!seeds.equals(newSeeds)) {
        (seeds -- newSeeds).foreach(s => log.info(s"A cluster seed lost: '$s'"))
        seeds = newSeeds
        cluster.joinSeedNodes(seeds.map(address => AddressFromURIString(s"akka.tcp://application@$address")).toList)
      }
      val master = try { Some(client.get("/jetchat/master").send().get().node.value) } catch { case e:EtcdException => None}
      if (master.isEmpty) {
        try {
          client.put("/jetchat/master", s"$selfHost:$selfPort").ttl((MASTER_TTL / 1000).toInt).prevExist(false).send().get()
          log.info(s"The cluster master is self-elected: '$selfHost:$selfPort'")
          if (!isMaster) {
            isMaster = true
            context.system.eventStream.publish(MasterEvent)
          }
        } catch { case e:EtcdException =>
        }
      } else if (master.get.equals(s"$selfHost:$selfPort")) {
        client.put("/jetchat/master", s"$selfHost:$selfPort").ttl((MASTER_TTL / 1000).toInt).prevValue(s"$selfHost:$selfPort").send().get()
      } else {
        isMaster = false
        context.system.eventStream.publish(SlaveEvent)
      }
    case _: MemberEvent => // ignore
  }
}