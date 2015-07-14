package controllers

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl._
import javax.ws.rs.client.ClientBuilder

import com.intellij.hub.auth.request.AuthRequestParameter.RequestCredentials
import jetbrains.jetpass.client.accounts.BaseAccountsClient
import jetbrains.jetpass.client.hub.HubClient
import jetbrains.jetpass.client.oauth2.OAuth2Client
import jetbrains.jetpass.client.oauth2.auth.OAuth2CodeFlow
import models.User
import play.api.libs.concurrent.Akka
import play.api.libs.json.{JsNumber, JsString, JsObject}
import play.api.mvc.{Cookie, RequestHeader, Action, Controller}

import scala.concurrent.Future
import play.api.Play.current
import play.api.db.slick.DB
import models.current.dao
import myUtils.MyPostgresDriver.simple._

object Auth extends Controller {
  lazy val HUB_BASE_URL = current.configuration.getString("hub.url").get

  lazy val HUB_SECRET = current.configuration.getString("hub.secret").get
  lazy val HUB_CLIENT_ID = current.configuration.getString("hub.clientId").get

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
    codeFlowBuilder.state(controllers.routes.Application.index().absoluteURL())

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
    val hubUser = hub._1.getAccountsClient(codeResponseFlow).getUserClient().me(null)
    DB.withSession { implicit session =>
      val user = dao.users.filter(_.login === hubUser.getLogin).firstOption
      user match {
        case None =>
          dao.users += new User(login = hubUser.getLogin, name = hubUser.getName, avatar = Option(hubUser.getAvatar.getUrl))
          val u = dao.users.filter(_.login === hubUser.getLogin).first
          Akka.system.actorSelection("/user/*.*") ! JsObject(Seq("newUser" -> JsObject(Seq("id" -> JsNumber(u.id), "name" -> JsString(u.name), "login" -> JsString(u.login)) ++
            (u.avatar match {
              case Some(value) => Seq("avatar" -> JsString(value))
              case None => Seq()
            }))))
        case _ =>
      }
    }
    Future.successful(Redirect(controllers.routes.Application.index()).withCookies(Cookie("user", hubUser.getLogin, httpOnly = false)))
  }
}
