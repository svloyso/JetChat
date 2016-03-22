import javax.inject.Inject

import actors._
import akka.actor.{ActorRef, ActorSystem, Props}
import _root_.api.Integration
import models.api.IntegrationTokensDAO
import models._
import play.api.Application
import play.api.libs.mailer.MailerClient

import scala.collection.JavaConversions._

class Global @Inject()(val system: ActorSystem, val application: Application,
                       val integrations: java.util.Set[Integration],
                       val integrationTokensDAO: IntegrationTokensDAO,
                       val integrationTopicsDAO: IntegrationTopicsDAO,
                       val integrationUpdatesDAO: IntegrationUpdatesDAO,
                       val integrationUsersDAO: IntegrationUsersDAO,
                       val integrationGroupsDAO: IntegrationGroupsDAO,
                       val topicsDAO: TopicsDAO, val commentsDAO: CommentsDAO,
                       val directMessagesDAO: DirectMessagesDAO,
                       val onlineUserRegistry: OnlineUserRegistry,
                       val mailerClient: MailerClient) {
  if (!play.api.Play.isTest(application)) {
    system.actorOf(Props(new OnlineUserRegistryActor(onlineUserRegistry)), "online-user-registry")
    system.actorOf(Props[ClusterListener], "cluster-listener")
    system.actorOf(IntegrationActor.props(integrations, integrationTokensDAO), "integration-actor")
    system.actorOf(Props(new EmailActor(topicsDAO, commentsDAO, directMessagesDAO, mailerClient, application)), "email-actor")

    for (integration <- integrations) {
      val integrationRef: ActorRef = MessagesActor.actorOf(integration, system, integrationTokensDAO, integrationTopicsDAO,
        integrationUpdatesDAO, integrationUsersDAO, integrationGroupsDAO)
    }
  }
}
