package controllers

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.inject.{Inject, Singleton}
import javax.net.ssl._
import javax.ws.rs.client.ClientBuilder

import actors.ClusterEvent
import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.intellij.hub.auth.request.AuthRequestParameter.RequestCredentials
import jetbrains.jetpass.client.accounts.BaseAccountsClient
import jetbrains.jetpass.client.hub.HubClient
import jetbrains.jetpass.client.oauth2.OAuth2Client
import jetbrains.jetpass.client.oauth2.auth.OAuth2CodeFlow
import models.{User, UsersDAO}
import play.api.Play.current
import play.api.libs.json.{JsNumber, JsObject, JsString}
import play.api.mvc.{Action, Controller, Cookie, RequestHeader}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class Auth @Inject()(val system: ActorSystem,
                     val usersDAO: UsersDAO) extends Controller {
  lazy val HUB_BASE_URL = current.configuration.getString("hub.url").getOrElse(System.getProperty("hub.url", ""))
  lazy val HUB_SECRET = current.configuration.getString("hub.secret").getOrElse(System.getProperty("hub.secret", ""))
  lazy val HUB_CLIENT_ID = current.configuration.getString("hub.clientId").getOrElse(System.getProperty("hub.clientId", ""))

  lazy val HUB_MOCK_LOGIN = current.configuration.getString("hub.mock.user.login").getOrElse(System.getProperty("hub.mock.user.login", ""))
  lazy val HUB_MOCK_NAME = current.configuration.getString("hub.mock.user.name").getOrElse(System.getProperty("hub.mock.user.name", ""))
  lazy val HUB_MOCK_AVATAR = current.configuration.getString("hub.mock.user.avatar").getOrElse(System.getProperty("hub.mock.user.avatar", ""))

  val mediator = DistributedPubSub(system).mediator

  def hub(implicit request: RequestHeader): (HubClient, OAuth2Client, BaseAccountsClient, OAuth2CodeFlow) = hubP(RequestCredentials.DEFAULT)

  def hubP(credentials: RequestCredentials)(implicit request: RequestHeader): (HubClient, OAuth2Client, BaseAccountsClient, OAuth2CodeFlow) = {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, Array(new X509TrustManager {
      override def getAcceptedIssuers: Array[X509Certificate] = {
        Array()
      }

      override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String) = Unit

      override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String) = Unit
    }), new SecureRandom())

    val hubClient = HubClient.builder(ClientBuilder.newBuilder().sslContext(sslContext)).baseUrl(HUB_BASE_URL).build()
    val oauthClient = hubClient.getOAuthClient

    val codeFlowBuilder = oauthClient.codeFlow()
    codeFlowBuilder.clientId(HUB_CLIENT_ID)
    codeFlowBuilder.redirectUri(controllers.routes.Auth.callback(None, None).absoluteURL())

    codeFlowBuilder.credentials(credentials)

    codeFlowBuilder.addScope("HUB")

    // TODO: Store a random state in session
    // TODO: Store group, topic, user
    codeFlowBuilder.state(controllers.routes.Application.index(None, None, None, None, None, None).absoluteURL())

    (hubClient, oauthClient, hubClient.getAccountsClient(HUB_CLIENT_ID, HUB_SECRET), codeFlowBuilder.build())
  }

  def getAuthUrl(implicit request: RequestHeader): String = {
    hub._4.getAuthUri.toASCIIString
  }

  def getLogoutUrl(implicit request: RequestHeader): String = {
    hubP(RequestCredentials.REQUIRED)._4.getAuthUri.toASCIIString
  }

  def callback(codeOpt: Option[String] = None, stateOpt: Option[String] = None) = Action.async { implicit request =>
    val codeHandler = hub._4.getCodeHandler
    val codeResponseFlow = codeHandler.exchange(HUB_SECRET, codeOpt.get, stateOpt.get)
    val token = codeResponseFlow.getToken
    val hubUser = hub._1.getAccountsClient(codeResponseFlow).getUserClient.me(null)
    // TODO: Chain futures
    usersDAO.findByLogin(hubUser.getLogin).map {
      case None =>
        usersDAO.insert(User(login = hubUser.getLogin, name = hubUser.getName, avatar = Option(hubUser.getAvatar.getUrl))) onSuccess { case id =>
          usersDAO.findByLogin(hubUser.getLogin).onSuccess { case Some(u) =>
            mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("newUser" -> JsObject(Seq("id" -> JsNumber(u.id), "name" -> JsString(u.name), "login" -> JsString(u.login)) ++ (u.avatar match {
              case Some(value) => Seq("avatar" -> JsString(value))
              case None => Seq()
            }))))))
          }
        }
    }
    Future.successful(Redirect(controllers.routes.Application.index(None, None, None, None, None, None)).withCookies(Cookie("user", hubUser.getLogin, httpOnly = false)))
  }
}
