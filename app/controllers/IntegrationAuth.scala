package controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}

import actors.{ActorUtils, ClusterEvent, IntegrationEnabled}
import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import api.{Integration, Utils}
import models.{IntegrationUser, User, IntegrationUsersDAO, UsersDAO}
import models.api.{IntegrationToken, IntegrationTokensDAO}
import play.api.libs.json.{JsNumber, JsString, JsObject}
import play.api.mvc.{DiscardingCookie, Cookie, Action, Controller}

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
                                integrationUsersDAO: IntegrationUsersDAO,
                                usersDAO: UsersDAO,
                                system: ActorSystem) extends Controller {
  val mediator = DistributedPubSub(system).mediator

  def logout(id: String, discardUserCookie: Boolean) = Action.async { implicit request =>
    integrations.toSeq.find(_.id == id) match {
      case Some(integration) =>
        var redirect = Redirect(controllers.routes.Application.index(None, None, None, None, None, None, None, None, None, None).absoluteURL(RequestUtils.secure))
        if (discardUserCookie) {
          redirect = redirect.discardingCookies(DiscardingCookie("user"))
        }
        request.cookies.get("user") match {
          case Some(cookie) =>
            val login = cookie.value
            usersDAO.findByLogin(login).flatMap {
              case Some(user) =>
                integrationTokensDAO.find(user.id, integration.id).flatMap {
                  case Some(token) =>
                    integrationTokensDAO.delete(token).flatMap { _ =>
                      integration.authentificator.logout(token.token).flatMap { _ =>
                        Future.successful(redirect)
                      }
                    }
                  case _ => {
                    // Already disabled
                    Future.successful(Redirect(controllers.routes.Application.index(None, None, None, None, None, None, None, None, None, None).absoluteURL(RequestUtils.secure)))
                  }
                }
              case None => Future.successful(BadRequest("Wrong user"))
            }
          case _ => {
            // User is already logged off
            Future.successful(redirect)
          }
        }
      case None => Future.successful(BadRequest("Wrong service"))
    }
  }

  def auth(id: String, redirectUrl: Option[String]) = Action.async { implicit request =>
    integrations.toSeq.find(_.id == id) match {
      case Some(integration) =>
        val state = UUID.randomUUID().toString
        integration.authentificator.auth(redirectUrl, state).map(_.withSession(s"${integration.id}-oauth-state" -> state))
      case None => Future.successful(BadRequest("Wrong service"))
    }
  }

  def enable(id: String, redirectUrl: Option[String]) = Action.async { implicit request =>
    integrations.toSeq.find(_.id == id) match {
      case Some(integration) =>
        request.cookies.get("user") match {
          case Some(cookie) =>
            val login = cookie.value
            usersDAO.findByLogin(login).flatMap {
              case Some(user) =>
                integrationTokensDAO.find(user.id, integration.id).flatMap {
                  case Some(token) =>
                    integrationTokensDAO.merge(IntegrationToken(token.userId, token.integrationId, token.token, true)).flatMap { _ =>
                       system.actorSelection("/user/integration-actor") ! IntegrationEnabled(token.userId, token.integrationId)
                       Future.successful(Redirect(redirectUrl.getOrElse(controllers.routes.Application.index(None, None, None, None, None, None, None, None, None, None).absoluteURL(RequestUtils.secure))))
                    }
                  case _ =>
                    val state = UUID.randomUUID().toString + ":enable"
                    integration.authentificator.auth(redirectUrl, state).map(_.withSession(s"${integration.id}-oauth-state" -> state))
                }
              case None => Future.successful(BadRequest("Wrong user"))
            }

          case None => Future.successful(BadRequest("User not authorized"))
        }
      case None => Future.successful(BadRequest("Wrong service"))
    }
  }

  def disable(id: String) = Action.async { implicit request =>
    integrations.toSeq.find(_.id == id) match {
      case Some(integration) =>
        request.cookies.get("user") match {
          case Some(cookie) =>
            val login = cookie.value
            usersDAO.findByLogin(login).flatMap {
              case Some(user) =>
                integrationTokensDAO.find(user.id, integration.id).flatMap {
                  case Some(token) =>
                    integrationTokensDAO.merge(IntegrationToken(token.userId, token.integrationId, token.token, false)).flatMap { _ =>
                      Future.successful(Ok)
                    }
                  case _ =>
                    Future.successful(BadRequest("Not authorized"))
                }
              case None => Future.successful(BadRequest("Wrong user"))
            }

          case None => Future.successful(BadRequest("Integration not authorized"))
        }
      case None => Future.successful(BadRequest("Wrong service"))
    }
  }

  def callback(integrationId: String, codeOpt: Option[String] = None, stateOpt: Option[String] = None,
               redirectUrl: Option[String] = None) = Action.async { implicit request =>
    val enable = stateOpt.isDefined && stateOpt.get.endsWith(":enable")
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
              (for {
                login <- integration.userHandler.login(accessToken)
                name <- integration.userHandler.name(accessToken)
                avatarUrl <- integration.userHandler.avatarUrl(accessToken)
                email <- integration.userHandler.email(accessToken)
              } yield (login, name, avatarUrl, email)).flatMap { case (integrationUserId, integrationName, integrationAvatarUrl, email) =>
                request.cookies.get("user") match {
                  case Some(cookie) =>
                    val login = cookie.value
                    (for {
                      user <- usersDAO.findByLogin(login).map(_.get)
                      userId = user.id
                      _ <- integrationUsersDAO.merge(IntegrationUser(integrationId, Some(userId), integrationUserId, integrationName.getOrElse(integrationUserId), integrationAvatarUrl))
                      result <- integrationTokensDAO.merge(IntegrationToken(userId, integrationId, accessToken, enable))
                    } yield {
                      if (enable) {
                        system.actorSelection("/user/integration-actor") ! IntegrationEnabled(userId, integrationId)
                        mediator ! Publish("cluster-events", ClusterEvent(ActorUtils.encodePath(user.login), JsObject(Seq("enableIntegration" -> JsString(integrationId)))))
                      }
                      result
                    }).map { _ =>
                      Redirect(redirectUrl.getOrElse(controllers.routes.Application.index(None, None, None, None, None, None, None, None, None, None).absoluteURL(RequestUtils.secure)))
                    }.recover { case e: Throwable => BadRequest(e.getMessage) }
                  case _ =>
                    integrationUsersDAO.findByIntegrationUserId(integrationUserId, integrationId).flatMap {
                      case Some(integrationUser) if integrationUser.userId.nonEmpty =>
                        val userId = integrationUser.userId.get
                        (for {
                          user <- usersDAO.findById(userId)
                          updated <- usersDAO.update(User(user.get.id, user.get.login, user.get.name, integrationAvatarUrl, email, isBot = false))
                          result <- integrationTokensDAO.merge(IntegrationToken(userId, integrationId, accessToken, enable))
                        } yield {
                          if (enable) {
                            system.actorSelection("/user/integration-actor") ! IntegrationEnabled(userId, integrationId)
                            mediator ! Publish("cluster-events", ClusterEvent(ActorUtils.encodePath(user.get.login), JsObject(Seq("enableIntegration" -> JsString(integrationId)))))
                          }
                          user.get.login
                        }).map { login =>
                          Redirect(redirectUrl.getOrElse(controllers.routes.Application.index(None, None, None, None, None, None, None, None, None, None).absoluteURL(RequestUtils.secure))).withCookies(
                            Cookie("user", login, httpOnly = false))
                        }.recover { case e: Throwable => BadRequest(e.getMessage) }
                      case _ =>
                        val loginFromIntegration = s"$integrationId/$integrationUserId"
                        usersDAO.insert(User(login = loginFromIntegration, name = integrationName.getOrElse(loginFromIntegration),
                          avatar = integrationAvatarUrl, email = email, isBot = false)) flatMap { case id =>
                          usersDAO.findByLogin(loginFromIntegration) flatMap { case Some(user) =>
                            mediator ! Publish("cluster-events", ClusterEvent("*",
                              JsObject(Seq("newUser" -> JsObject(Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
                                (user.avatar match {
                                  case Some(value) => Seq("avatar" -> JsString(value))
                                  case None => Seq()
                                }))))))
                            val userId = user.id
                            (for {
                              _ <- integrationUsersDAO.merge(IntegrationUser(integrationId, Some(id), integrationUserId, integrationName.getOrElse(integrationUserId), integrationAvatarUrl))
                              result <- integrationTokensDAO.merge(IntegrationToken(userId, integrationId, accessToken, enable))
                            } yield {
                              if (enable) {
                                system.actorSelection("/user/integration-actor") ! IntegrationEnabled(userId, integrationId)
                                mediator ! Publish("cluster-events", ClusterEvent(ActorUtils.encodePath(user.login), JsObject(Seq("enableIntegration" -> JsString(integrationId)))))
                              }
                              result
                            }).map { _ =>
                              Redirect(redirectUrl.getOrElse(controllers.routes.Application.index(None, None, None, None, None, None, None, None, None, None).absoluteURL(RequestUtils.secure))).withCookies(
                                Cookie("user", loginFromIntegration, httpOnly = false))
                            }.recover { case e: Throwable => BadRequest(e.getMessage) }
                          }
                        }
                    }
                }
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
