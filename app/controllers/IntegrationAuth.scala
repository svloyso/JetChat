package controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}

import actors.{ClusterEvent, IntegrationEnabled}
import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import api.{Integration, Utils}
import models.UsersDAO
import models.api.{IntegrationToken, IntegrationTokensDAO}
import play.api.libs.json.{JsString, JsObject}
import play.api.mvc.{Action, Controller}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * @author Alefas
 * @since  15/09/15
 */
@Singleton
class IntegrationAuth @Inject()(integrations: java.util.Set[Integration],
                               integrationTokensDAO: IntegrationTokensDAO,
                               usersDAO: UsersDAO,
                               system: ActorSystem) extends Controller {
  val mediator = DistributedPubSub(system).mediator

  def auth(id: String, redirectUrl: Option[String]) = Action.async { implicit request =>
    integrations.toSeq.find(_.id == id) match {
      case Some(integration) =>
        val state = UUID.randomUUID().toString
        integration.authentificator.enable(redirectUrl, state).map(_.withSession(s"${integration.id}-oauth-state" -> state))
      case None => Future.successful(BadRequest("Wrong service"))
    }
  }

  def callback(integrationId: String, codeOpt: Option[String] = None, stateOpt: Option[String] = None,
               redirectUrl: Option[String] = None) = Action.async { implicit request =>
    integrations.find(_.id == integrationId) match {
      case Some(integration) =>
        (for {
          code <- codeOpt
          state <- stateOpt
          oauthState <- request.session.get(s"$integrationId-oauth-state")
        } yield {
          if (state == oauthState) {
            val callbackUrl = Utils.callbackUrl(integrationId, redirectUrl)
            integration.authentificator.token(callbackUrl, code).flatMap { accessToken =>
              request.cookies.get("user") match {
                case Some(cookie) =>
                  val login = cookie.value
                  (for {
                    user <- usersDAO.findByLogin(login).map(_.get)
                    userId = user.id
                    result <- integrationTokensDAO.merge(IntegrationToken(userId, integrationId, accessToken))
                  } yield {
                    system.actorSelection("/user/integration-actor") ! IntegrationEnabled(userId, integrationId)
                    mediator ! Publish("cluster-events", ClusterEvent(user.login, JsObject(Seq("newIntegration" -> JsString(integrationId)))))
                    result
                  }).map { _ =>
                    Redirect(controllers.routes.Application.index(None, None, None))
                  }.recover { case e: Throwable => BadRequest(e.getMessage) }
                case _ => Future.successful(BadRequest("User is logged off"))
              }
            }.recover {
              case ex: IllegalStateException => Unauthorized(ex.getMessage)
            }
          } else {
            Future.successful(BadRequest(s"Invalid $integrationId login"))
          }
          }).getOrElse(Future.successful(BadRequest("No parameters supplied")))
      case None => Future.successful(BadRequest("Wrong service"))
    }
  }
}
