package actors

import java.util

import akka.actor.{Props, Actor}
import akka.actor.Actor.Receive
import api.Integration
import models.UsersDAO
import models.api.IntegrationTokensDAO
import scala.collection.JavaConversions._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits._

/**
 * @author Alefas
 * @since  17/09/15
 */
class IntegrationActor(integrations: util.Set[Integration], integrationTokensDAO: IntegrationTokensDAO) extends Actor {
  override def receive: Receive = {
    case IntegrationEnabled(userId, integrationId) =>
      for {
        integrationTokenOption <- integrationTokensDAO.find(userId, integrationId)
      } yield {
        for {
          integrationToken <- integrationTokenOption
          integration <- integrations.find(_.id == integrationId)
        } integration.hookHandler.foreach { _.init(integrationToken.token) }
      }
    case _ => //todo: hook handled case
  }
}

object IntegrationActor {
  def props(integrations: util.Set[Integration], integrationTokensDAO: IntegrationTokensDAO): Props =
    Props(new IntegrationActor(integrations, integrationTokensDAO))
}

case class IntegrationEnabled(userId: Long, integrationId: String)
