import javax.inject.Inject

import actors._
import akka.actor.{ActorRef, ActorSystem, Props}
import api.Integration
import models.api.IntegrationTokensDAO
import models.{IntegrationGroupsDAO, IntegrationTopicsDAO, IntegrationUpdatesDAO, IntegrationUsersDAO}
import play.api.Application

import scala.collection.JavaConversions._

class Global @Inject()(val system: ActorSystem, val application: Application,
                       val integrations: java.util.Set[Integration],
                       val integrationTokensDAO: IntegrationTokensDAO,
                       val integrationTopicsDAO: IntegrationTopicsDAO,
                       val integrationUpdatesDAO: IntegrationUpdatesDAO,
                       val integrationUsersDAO: IntegrationUsersDAO,
                       val integrationGroupsDAO: IntegrationGroupsDAO) {
  if (!play.api.Play.isTest(application)) {
    system.actorOf(Props[ClusterListener], "cluster-listener")
    system.actorOf(IntegrationActor.props(integrations, integrationTokensDAO), "integration-actor")

    for (integration <- integrations) {
      val integrationRef: ActorRef = MessagesActor.actorOf(integration, system, integrationTokensDAO, integrationTopicsDAO,
        integrationUpdatesDAO, integrationUsersDAO, integrationGroupsDAO)
    }
  }
}
