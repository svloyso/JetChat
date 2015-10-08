import javax.inject.Inject

import actors.{ReceiveMessagesEvent, MessagesActor, IntegrationActor, ClusterListener}
import akka.actor.{ActorSystem, Props}
import api.Integration
import models.api.IntegrationTokensDAO
import play.api.Application

import scala.collection.JavaConversions._

class Global @Inject()(val system: ActorSystem, val application: Application,
                       val integrations: java.util.Set[Integration],
                       val integrationTokensDAO: IntegrationTokensDAO) {
  if (!play.api.Play.isTest(application)) {
    system.actorOf(Props[ClusterListener], "cluster-listener")
    system.actorOf(IntegrationActor.props(integrations, integrationTokensDAO), "integration-actor")
    for (integration <- integrations) {
      MessagesActor.actorOf(integration, system, integrationTokensDAO) ! ReceiveMessagesEvent
    }
  }
}
