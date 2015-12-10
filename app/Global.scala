import javax.inject.Inject

import actors.{ClusterListener, IntegrationActor, MessagesActor, ReceiveMessagesEvent}
import akka.actor.{ActorSystem, Props}
import api.Integration
import models.api.IntegrationTokensDAO
import models.{IntegrationGroupsDAO, IntegrationTopicsDAO, IntegrationUpdatesDAO, IntegrationUsersDAO}
import play.api.Application

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration.DurationInt

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
      system.scheduler.scheduleOnce(10.seconds, new Runnable {
        override def run(): Unit = {
          MessagesActor.actorOf(integration, system, integrationTokensDAO, integrationTopicsDAO,
            integrationUpdatesDAO, integrationUsersDAO, integrationGroupsDAO) ! ReceiveMessagesEvent
        }
      })
    }
  }
}
