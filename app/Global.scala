import javax.inject.Inject

import actors.{MessagesActor, IntegrationActor, ClusterListener}
import akka.actor.{ActorSystem, Props}
import api.Integration
import models.api.IntegrationTokensDAO
import play.api.Application

class Global @Inject()(val system: ActorSystem, val application: Application,
                       val integrations: java.util.Set[Integration],
                       val integrationTokensDAO: IntegrationTokensDAO) {
  if (!play.api.Play.isTest(application)) {
    system.actorOf(Props[ClusterListener], "cluster-listener")
    system.actorOf(IntegrationActor.props(integrations, integrationTokensDAO), "integration-actor")
    system.actorOf(MessagesActor.props(integrations, integrationTokensDAO), "messages-actor")
  }
}
