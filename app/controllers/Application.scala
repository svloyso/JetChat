package controllers

import java.sql.Timestamp
import java.util.Calendar
import javax.inject.{Inject, Singleton}

import _root_.api.Integration
import actors._
import akka.actor.{ActorSystem, PoisonPill}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.typesafe.config.ConfigRenderOptions
import models._
import models.api.IntegrationTokensDAO
import play.api.{Play, Logger}
import play.api.libs.functional.syntax._
import play.api.libs.iteratee.{Concurrent, Iteratee}
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Singleton
class Application @Inject()(val system: ActorSystem, integrations: java.util.Set[Integration],
                            val usersDAO: UsersDAO, val groupsDAO: GroupsDAO,
                            val topicsDAO: TopicsDAO, val commentsDAO: CommentsDAO,
                            val directMessagesDAO: DirectMessagesDAO,
                            val integrationTopicsDAO: IntegrationTopicsDAO,
                            val integrationTokensDAO: IntegrationTokensDAO,
                            val integrationGroupsDAO: IntegrationGroupsDAO,
                            val integrationUpdatesDAO: IntegrationUpdatesDAO,
                            val integrationUsersDAO: IntegrationUsersDAO) extends Controller {

  implicit val tsReads: Reads[Timestamp] = Reads.of[Long] map (new Timestamp(_))
  implicit val tsWrites: Writes[Timestamp] = Writes { (ts: Timestamp) => JsString(ts.toString) }

  implicit val userReads: Reads[User] = (
    (JsPath \ "id").read[Long] and
      (JsPath \ "login").read[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "avatar").readNullable[String]
    ) (User.apply _)
  implicit val userWrites: Writes[User] = (
    (JsPath \ "id").write[Long] and
      (JsPath \ "login").write[String] and
      (JsPath \ "name").write[String] and
      (JsPath \ "avatar").writeNullable[String]
    ) (unlift(User.unapply))

  implicit val topicReads: Reads[Topic] = (
    (JsPath \ "id").read[Long] and
      (JsPath \ "groupId").read[Long] and
      (JsPath \ "userId").read[Long] and
      (JsPath \ "date").read[Timestamp] and
      (JsPath \ "text").read[String]
    ) (Topic.apply _)
  implicit val topicWrites: Writes[Topic] = (
    (JsPath \ "id").write[Long] and
      (JsPath \ "groupId").write[Long] and
      (JsPath \ "userId").write[Long] and
      (JsPath \ "date").write[Timestamp] and
      (JsPath \ "text").write[String]
    ) (unlift(Topic.unapply))

  implicit val integrationTopicReads: Reads[IntegrationTopic] = (
    (JsPath \ "id").read[Long] and
    (JsPath \ "integrationId").read[String] and
      (JsPath \ "integrationTopicId").read[String] and
      (JsPath \ "integrationGroupId").read[String] and
      (JsPath \ "userId").read[Long] and
      (JsPath \ "integrationUserId").read[String] and
      (JsPath \ "date").read[Timestamp] and
      (JsPath \ "text").read[String] and
      (JsPath \ "title").read[String]
    ) (IntegrationTopicFactory.applyNoOption _)
  implicit val integrationTopicWrites: Writes[IntegrationTopic] = (
    (JsPath \ "id").write[Long] and
    (JsPath \ "integrationId").write[String] and
      (JsPath \ "integrationTopicId").write[String] and
      (JsPath \ "integrationGroupId").write[String] and
      (JsPath \ "userId").write[Long] and
      (JsPath \ "integrationUserId").write[String] and
      (JsPath \ "date").write[Timestamp] and
      (JsPath \ "text").write[String] and
      (JsPath \ "title").write[String]
  )(unlift(IntegrationTopicFactory.unapplyNoOption))

  val TICK = JsString("Tick")
  val TACK = JsString("Tack")
  val DEFAULT_STATE = "chat"

  val mediator = DistributedPubSub(system).mediator

  var actorCounter = 0

  def index(state: Option[String],
            groupId: Option[Long] = None,
            topicId: Option[Long] = None,
            userTopicId: Option[Long] = None,
            userId: Option[Long] = None,
            integrationId: Option[String] = None,
            integrationGroupId: Option[String] = None,
            integrationTopicGroupId: Option[String] = None,
            integrationTopicId: Option[String] = None,
            query: Option[String] = None) = Action.async { implicit request =>
    request.cookies.get("user") match {
      case Some(cookie) =>
        usersDAO.findByLogin(cookie.value).map {
          case Some(user) =>
            val webSocketUrl = routes.Application.webSocket(user.login).absoluteURL(RequestUtils.secure).replaceAll("http", "ws")
              Ok(views.html.index(user, state.getOrElse(DEFAULT_STATE), groupId, topicId, userId, userTopicId, integrationId,
                integrationGroupId, integrationTopicGroupId, integrationTopicId, webSocketUrl))
          case None =>
            Redirect(controllers.routes.Application.index(None, None, None, None, None, None, None,
              None, None, None).absoluteURL(RequestUtils.secure)).discardingCookies(DiscardingCookie("user"))
         }
      case _ =>
        val integration = integrations.iterator().next() //todo[Alefas]: implement UI to choose integrations!
      val redirectUrl = controllers.routes.Application.index(None, None, None, None,
          None, None, None, None, None, None).absoluteURL(RequestUtils.secure)
        Future.successful(Redirect(controllers.routes.IntegrationAuth.auth(integration.id, Option(redirectUrl))))
    }
  }

  def getInitialState(userId: Long,
                      groupId: Option[Long],
                      integrationId: Option[String] = None,
                      integrationTopicGroupId: Option[String] = None,
                      integrationTopicId: Option[String] = None) = Action.async { implicit request =>
    (for {
      users <- getUsersJsValue(userId)
      groups <- getGroupsJsValue(userId, None)
      topics <- if (integrationId.isEmpty) (
        if (groupId.isDefined) getGroupTopicsJsValue(userId, groupId.get, None)
        else getAllTopicsJsValue(userId, None))
      else Future.successful(JsNull)
      integrations <- getUserIntegrationsJson(userId)
      integrationGroups <- getIntegrationGroupsJsValue(userId, None)
      integrationTopic <- integrationTopicId match {
        case Some(value) => integrationTopicsDAO.find(integrationId.get, integrationTopicGroupId.get, value, userId)
        case None => Future.successful(None)
      }
    } yield (integrations, users, groups, integrationGroups, integrationTopic, topics)
      ) map {
      case (userIntegrations, users, groups, integrationGroups, integrationTopic, topics) =>
        Ok(Json.toJson(JsObject(Seq(
          "users" -> users,
          "groups" -> groups,
          "topics" -> topics,
          "integrations" -> userIntegrations,
          "integrationGroups" -> integrationGroups,
          "integrationTopic" -> (integrationTopic match {
            case Some(value) => Json.toJson(value)
            case None => JsNull
          })
        ))))
    }
  }

  def webSocket(login: String) = WebSocket.using[JsValue] { request =>
    val (out, channel) = Concurrent.broadcast[JsValue]

    actorCounter += 1
    val actor = system.actorOf(WebSocketActor.props(channel), s"${ActorUtils.encodePath(login)}.$actorCounter")

    val in = Iteratee.foreach[JsValue] { message =>
      if (message.equals(TICK))
        channel.push(TACK)
    } map { _ =>
      actor ! PoisonPill
    }

    (in, out)
  }

  def logout() = Action.async { implicit request =>
    val integration = integrations.iterator().next() //todo[Alefas]: implement UI to choose integrations!
    Future.successful(Redirect(controllers.routes.IntegrationAuth.logout(integration.id, true).absoluteURL(RequestUtils.secure)))
  }

  def getUser(login: String) = Action.async { implicit request =>
    usersDAO.findByLogin(login).map {
      case Some(user) => Ok(Json.toJson(user))
      case None => NoContent
    }
  }

  def getUsers(userId: Long) = Action.async { implicit request =>
    getUsersJsValue(userId).map(Ok(_))
  }

  def getUsersJsValue(userId: Long): Future[JsValue] = {
    usersDAO.allWithCounts(userId).map { case userChats =>
      Json.toJson(JsArray(userChats.map { case UserChat(user, text, updateDate, unreadCount) =>
        JsObject(
          Seq("id" -> JsNumber(user.id),
            "login" -> JsString(user.login),
            "name" -> JsString(user.name),
            "avatar" -> (if (user.avatar.isDefined) JsString(user.avatar.get) else JsNull),
            "unreadCount" -> JsNumber(unreadCount)
          ) ++ (user.avatar match {
            case Some(value) => Seq("avatar" -> JsString(value))
            case None => Seq()
          })
        )
      }))
    }
  }

  def getGroups(userId: Long, query: Option[String]) = Action.async { implicit request =>
    getGroupsJsValue(userId, query).map(Ok(_))
  }

  def getGroupsJsValue(userId: Long, query: Option[String]): Future[JsValue] = {
    groupsDAO.allWithCounts(userId, query).map { f =>
      Json.toJson(JsArray(f.map { case (group, readCount, count) => JsObject(Seq("id" -> JsNumber(group.id),
        "name" -> JsString(group.name), "unreadCount" -> JsNumber(count - readCount), "count" -> JsNumber(count)))
      }))
    }
  }

  def getIntegrationGroups(userId: Long, query: Option[String]) = Action.async { implicit request =>
    getIntegrationGroupsJsValue(userId, query).map(Ok(_))
  }

  def getIntegrationGroupsJsValue(userId: Long, query: Option[String]): Future[JsValue] = {
    integrationGroupsDAO.allWithCounts(userId, query).map { f =>
      Json.toJson(JsArray(f.map { case (group, count) => JsObject(Seq("integrationId" -> JsString(group.integrationId),
        "integrationGroupId" -> JsString(group.integrationGroupId),
        "name" -> JsString(group.name), "count" -> JsNumber(count)))
      }))
    }
  }

  def getAllTopics(userId: Long, query: Option[String]) = Action.async { implicit request =>
    getAllTopicsJsValue(userId, query).map(Ok(_))
  }

  def getAllTopicsJsValue(userId: Long, query: Option[String]): Future[JsValue] = {
    val topics = query match {
      case Some(str) => topicsDAO.allWithCounts(userId, None, str)
      case None => topicsDAO.allWithCounts(userId, None)
    }

    topics.flatMap { topicChats =>
      usersDAO.allWithCounts(userId, nonEmptyOnly = true).map { userTopics =>
        Json.toJson(JsArray((topicChats ++ userTopics).sortBy(-_.updateDate.getTime).map { case TopicChat(topic, group, user, updateDate, unread, unreadCount) =>
          JsObject(Seq("topic" -> JsObject(Seq("id" -> JsNumber(topic.id), "date" -> JsNumber(topic.date.getTime), "group" -> JsObject
          (Seq("id" -> JsNumber(group.id), "name" -> JsString(group.name))),
            "text" -> JsString(topic.text), "user" -> JsObject(Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name))))),
            "updateDate" -> JsNumber(updateDate.getTime),
            "unread" -> JsBoolean(unread),
            "unreadCount" -> JsNumber(unreadCount)))
        case UserChat(user, text, updateDate, unreadCount) =>
          JsObject(Seq("userTopic" -> JsObject(Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "text" -> JsString(text), "avatar" -> (if (user.avatar.isDefined) JsString(user.avatar.get) else JsNull))),
            "updateDate" -> JsNumber(updateDate.getTime),
            "unreadCount" -> JsNumber(unreadCount)))
        }))
      }
    }
  }

  def getGroupTopics(userId: Long, groupId: Long, query: Option[String]) = Action.async { implicit rs =>
    getGroupTopicsJsValue(userId, groupId, query).map(Ok(_))
  }

  def getGroupTopicsJsValue(userId: Long, groupId: Long, query: Option[String]): Future[JsValue] = {
    val topics = query match {
      case Some(str) => topicsDAO.allWithCounts(userId, Some(groupId), str)
      case None => topicsDAO.allWithCounts(userId, Some(groupId))
    }

    topics.map { topicChats =>
      Json.toJson(JsArray(topicChats.map { case TopicChat(topic, group, user, updateDate, unread, unreadCount) =>
        JsObject(Seq("topic" -> JsObject(Seq("id" -> JsNumber(topic.id), "date" -> JsNumber(topic.date.getTime), "group" -> JsObject
        (Seq("id" -> JsNumber(group.id), "name" -> JsString(group.name))),
          "text" -> JsString(topic.text), "user" -> JsObject(Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name))))),
          "updateDate" -> JsNumber(updateDate.getTime),
          "unread" -> JsBoolean(unread),
          "unreadCount" -> JsNumber(unreadCount)))
      }))
    }
  }

  def getAllIntegrationTopics(userId: Long, query: Option[String]) = getIntegrationTopics(userId, None, None, query)

  def getIntegrationTopics(userId: Long,
                           integrationId: Option[String],
                           groupId: Option[String],
                           query: Option[String]) = Action.async { implicit rs =>
    integrationTopicsDAO.allWithCounts(userId, integrationId, groupId, query).map { f =>
      Json.toJson(JsArray(f.map { case (topicIntegrationId, topicId, topicDate, topicText, gId, groupName, integrationUserId, integrationUserName, uId, userName, c) =>
        var topic = JsObject(topicId.toSeq.map("id" -> JsString(_)) ++ Seq("integrationId" -> JsString(topicIntegrationId), "date" -> JsNumber(topicDate.getTime), "group" -> JsObject
        (Seq("id" -> JsString(gId), "name" -> JsString(groupName))),
          "text" -> JsString(topicText),
          "integrationUser" -> JsObject(Seq("id" -> JsString(integrationUserId), "name" -> JsString(integrationUserName)))))
        if (uId.isDefined) {
          topic = topic ++ Json.obj("user" -> JsObject(Seq("id" -> JsNumber(uId.get), "name" -> JsString(userName.get))))
        }
        JsObject(Seq("topic" -> topic, "updates" -> JsNumber(c)))
      }))
    }.map(Ok(_))
  }

  def getIntegrationGroupTopics(userId: Long,
                                integrationId: String,
                                groupId: Option[String],
                                query: Option[String]) = getIntegrationTopics(userId, Some(integrationId), groupId, query)

  def getMessages(userId: Long, topicId: Long, query: Option[String]) = Action.async { implicit request =>
    topicsDAO.messages(userId, topicId, query).map { f =>
      Ok(Json.toJson(JsArray(f.map { case (message, user, group, read) =>
        val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
          (user.avatar match {
            case Some(value) => Seq("avatar" -> JsString(value))
            case None => Seq()
          })
        val fields = Seq("id" -> JsNumber(message.id),
          "group" -> JsObject(Seq("id" -> JsNumber(group.id), "name" -> JsString(group.name))),
          "user" -> JsObject(userJson),
          "date" -> JsNumber(message.date.getTime),
          "text" -> JsString(message.text),
          "unread" -> JsBoolean(!read)) ++ (message match {
          case c: Comment =>
            Seq("topicId" -> JsNumber(c.topicId))
          case _ => Seq()
        })
        JsObject(fields)
      })))
    }
  }

  def markAsRead = Action.async(parse.json) { implicit request =>
    val userId = (request.body \ "userId").as[Long]
    val topicIds = (request.body \ "topicIds").as[Seq[Long]]
    val messageIds = (request.body \ "messageIds").as[Seq[Long]]
    val directMessageIds = (request.body \ "directMessageIds").as[Seq[Long]]
    Logger.debug(s"Marking topics and messages as read: user: $userId, topics: $topicIds, messages: $messageIds, directMessageIds: $directMessageIds")
    topicsDAO.markAsRead(userId, topicIds).flatMap(_ => commentsDAO.markAsRead(userId, messageIds).flatMap(_ => directMessagesDAO.markAsRead(directMessageIds).map(_ => Ok)))
  }

  def getIntegrationMessages(
      userId: Long,
      integrationId: String,
      integrationGroupId: String,
      integrationTopicId: String,
      query: Option[String])
  = Action.async { implicit request =>
    integrationTopicsDAO.messages(userId, integrationId, integrationGroupId, integrationTopicId, query).map { f =>
      Ok(Json.toJson(JsArray(f.map { case (message, user, group) =>
        val integrationUserJson = Seq("integrationUserId" -> JsString(user.integrationUserId), "name" -> JsString(user.name)) ++
          (user.avatar match {
            case Some(value) => Seq("avatar" -> JsString(value))
            case None => Seq()
          })
        val fields = Seq("group" -> JsObject(Seq("integrationId" -> JsString(group.integrationId),
          "integrationGroupId" -> JsString(group.integrationGroupId), "name" -> JsString(group.name))),
          "integrationTopicId" -> JsString(message.integrationTopicId.orNull),
          "integrationUser" -> JsObject(integrationUserJson),
          "date" -> JsNumber(message.date.getTime),
          "text" -> JsString(message.text)) ++ (message match {
          case u: IntegrationUpdate =>
            Seq("id" -> JsNumber(u.id))
          case _ => Seq()
        })
        JsObject(fields)
      })))
    }
  }

  def addGroup() = Action.async { implicit rs =>
    val groupName = rs.body.asJson.get.asInstanceOf[JsString].value
    groupsDAO.insert(Group(name = groupName)).map { case id =>
      Logger.debug(s"Adding group: $groupName")
      val groupJson = JsObject(Seq("id" -> JsNumber(id), "name" -> JsString(groupName)))
      mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("newGroup" -> groupJson))))
      Ok(Json.toJson(groupJson))
    }
  }

  def addComment() = Action.async(parse.json) { implicit request =>
    val userId = (request.body \ "user" \ "id").get.asInstanceOf[JsNumber].value.toLong
    val groupId = (request.body \ "group" \ "id").get.asInstanceOf[JsNumber].value.toLong
    val topicId = (request.body \ "topicId").get.asInstanceOf[JsNumber].value.toLong
    val text = (request.body \ "text").get.asInstanceOf[JsString].value
    val date = new Timestamp(Calendar.getInstance.getTime.getTime)
    commentsDAO.insert(Comment(groupId = groupId, userId = userId, topicId = topicId, date = date, text = text)).flatMap { case id =>
      Logger.debug(s"Adding comment: $userId, $groupId, $topicId, $text")
      usersDAO.findById(userId).map { case option =>
        val user = option.get
        val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
          (user.avatar match {
            case Some(value) => Seq("avatar" -> JsString(value))
            case None => Seq()
          })
        mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("id" -> JsNumber(id),
          "group" -> JsObject(Seq("id" -> JsNumber(groupId))),
          "topicId" -> JsNumber(topicId),
          "user" -> JsObject(userJson),
          "date" -> JsNumber(date.getTime),
          "text" -> JsString(text)))))
        Ok(Json.toJson(JsNumber(id)))

      }
    }
  }

  def addDirectMessage() = Action.async(parse.json) { implicit request =>
    val fromUserId = (request.body \ "user" \ "id").get.asInstanceOf[JsNumber].value.toLong
    val toUserId = (request.body \ "toUser" \ "id").get.asInstanceOf[JsNumber].value.toLong
    val text = (request.body \ "text").get.asInstanceOf[JsString].value
    val date = new Timestamp(Calendar.getInstance.getTime.getTime)
    directMessagesDAO.insert(DirectMessage(fromUserId = fromUserId, toUserId = toUserId, date = date, text = text)).flatMap {
      case id =>
        (for {
          user <- usersDAO.findById(fromUserId)
          toUser <- usersDAO.findById(toUserId)
        } yield (user, toUser)).map { case (u, tU) =>
          val user = u.get
          val toUser = tU.get
          Logger.debug(s"Adding direct message: $fromUserId, $toUserId, $text")
          val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
            (user.avatar match {
              case Some(value) => Seq("avatar" -> JsString(value))
              case None => Seq()
            })
          val toUserJson = Seq("id" -> JsNumber(toUser.id), "name" -> JsString(toUser.name), "login" -> JsString(toUser.login)) ++
            (toUser.avatar match {
              case Some(value) => Seq("avatar" -> JsString(value))
              case None => Seq()
            })
          val message = JsObject(Seq("id" -> JsNumber(id),
            "user" -> JsObject(userJson),
            "toUser" -> JsObject(toUserJson),
            "date" -> JsNumber(date.getTime),
            "text" -> JsString(text)))
          mediator ! Publish("cluster-events", ClusterEvent(ActorUtils.encodePath(user.login), message))
          mediator ! Publish("cluster-events", ClusterEvent(ActorUtils.encodePath(toUser.login), message))
          Ok(Json.toJson(JsNumber(id)))
        }
    }
  }

  def getDirectMessages(fromUserId: Long, toUserId: Long, query: Option[String]) = Action.async { implicit request =>
    directMessagesDAO.messages(fromUserId, toUserId).map { case seq =>
      Ok(Json.toJson(JsArray(seq.map { case (message, readStatus, fromUser, toUser) =>
        val fromUserJson = Seq("id" -> JsNumber(fromUser.id), "name" -> JsString(fromUser.name), "login" -> JsString(fromUser.login)) ++
          (fromUser.avatar match {
            case Some(value) => Seq("avatar" -> JsString(value))
            case None => Seq()
          })
        val toUserJson = Seq("id" -> JsNumber(toUser.id), "name" -> JsString(toUser.name), "login" -> JsString(toUser.login)) ++
          (toUser.avatar match {
            case Some(value) => Seq("avatar" -> JsString(value))
            case None => Seq()
          })
        val fields = Seq("id" -> JsNumber(message.id),
          "user" -> JsObject(fromUserJson),
          "toUser" -> JsObject(toUserJson),
          "date" -> JsNumber(message.date.getTime),
          "text" -> JsString(message.text),
          "unread" -> JsBoolean(!readStatus))
        JsObject(fields)
      })))
    }
  }

  def addTopic() = Action.async(parse.json) { implicit request =>
    val userId = (request.body \ "user" \ "id").get.asInstanceOf[JsNumber].value.toLong
    val groupId = (request.body \ "group" \ "id").get.asInstanceOf[JsNumber].value.toLong
    val text = (request.body \ "text").get.asInstanceOf[JsString].value
    val date = new Timestamp(Calendar.getInstance.getTime.getTime)
    topicsDAO.insert(Topic(groupId = groupId, userId = userId, date = date, text = text)).flatMap { case id =>
      usersDAO.findById(userId).map { case u =>
        val user = u.get
        Logger.debug(s"Adding topic: $userId, $groupId, $text")
        val userJson = Seq("id" -> JsNumber(user.id), "name" -> JsString(user.name), "login" -> JsString(user.login)) ++
          (user.avatar match {
            case Some(value) => Seq("avatar" -> JsString(value))
            case None => Seq()
          })
        mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq("id" -> JsNumber(id),
          "group" -> JsObject(Seq("id" -> JsNumber(groupId))),
          "user" -> JsObject(userJson),
          "date" -> JsNumber(date.getTime),
          "text" -> JsString(text)))))
        Ok(Json.toJson(JsNumber(id)))
      }
    }
  }

  def getUserIntegrationsJson(userId: Long): Future[JsValue] = integrationTokensDAO.find(userId).map { integrations =>
    Json.toJson(JsArray(integrations.map { case (i, t) => JsObject(Seq(
      "id" -> JsString(i.id),
      "name" -> JsString(i.name),
      "enabled" -> JsBoolean(t.isDefined && t.get.enabled))) }.toSeq))
  }

  def getUserIntegrations(userId: Long): Future[Map[Integration, Boolean]] = {
    integrationTokensDAO.find(userId).map {
      _.map { case (i, t) => i -> (t.isDefined && t.get.enabled) }
    }
  }

  def addIntegrationComment(integrationId: String) = Action.async(parse.json) { implicit request =>
    val userId = (request.body \ "user" \ "id").get.asInstanceOf[JsNumber].value.toLong
    val integrationGroupId = (request.body \ "integrationGroupId").as[String]
    val integrationTopicId = (request.body \ "integrationTopicId").as[String]
    val text = (request.body \ "text").get.asInstanceOf[JsString].value
    val date = new Timestamp(Calendar.getInstance.getTime.getTime)
    import scala.collection.JavaConversions._
    integrations.find(_.id == integrationId) match {
      case Some(integration) =>
        (for {
          topicOption <- integrationTopicsDAO.find(integrationId, integrationGroupId, integrationTopicId, userId)
          if topicOption.isDefined
          topic = topicOption.get
          tokenOption <- integrationTokensDAO.find(userId, integrationId)
          if tokenOption.isDefined
          token = tokenOption.get
          if token.enabled
          integrationUserIdOption <- integrationUsersDAO.findByUserId(userId, integrationId)
          if integrationUserIdOption.isDefined
          integrationUserId = integrationUserIdOption.get
          integrationUpdate = IntegrationUpdate(0, integrationId, None, integrationGroupId, integrationTopicId, userId, integrationUserId.integrationUserId, date, text)
          result <- integrationUpdatesDAO.insert(integrationUpdate)
        } yield {
          mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification
          MessagesActor.actorSelection(integration, system) ! SendMessage(userId, integrationGroupId, integrationTopicId, text, result)
          Created("Good!")
        }).recover {
          case t: Throwable =>
            BadRequest(t.getMessage)
        }
      case None => Future(BadRequest("No integration for this id"))
    }
  }

  def addIntegrationTopic(integrationId: String) = Action.async(parse.json) { implicit request =>
    /*val userId = (request.body \ "user" \ "id").get.asInstanceOf[JsNumber].value.toLong
    val integrationGroupId = (request.body \ "integrationGroupId").as[String]
    val text = (request.body \ "text").get.asInstanceOf[JsString].value
    val date = new Timestamp(Calendar.getInstance.getTime.getTime)
    import scala.collection.JavaConversions._
    integrations.find(_.id == integrationId) match {
      case Some(integration) =>
        (for {
          topicOption <- integrationTopicsDAO.find(integrationId, integrationGroupId, integrationTopicId, userId)
          if topicOption.isDefined
          topic = topicOption.get
          tokenOption <- integrationTokensDAO.find(userId, integrationId)
          if tokenOption.isDefined
          token = tokenOption.get
          if token.enabled
          integrationUserIdOption <- integrationUsersDAO.findByUserId(userId, integrationId)
          if integrationUserIdOption.isDefined
          integrationUserId = integrationUserIdOption.get
          integrationUpdate = IntegrationUpdate(0, integrationId, None, integrationGroupId, integrationTopicId, userId, integrationUserId.integrationUserId, date, text)
          result <- integrationUpdatesDAO.insert(integrationUpdate)
        } yield {
          mediator ! Publish("cluster-events", ClusterEvent("*", JsObject(Seq()))) //todo: add proper notification
          MessagesActor.actorSelection(integration, system) ! SendMessage(userId, integrationGroupId, integrationTopicId, text, result)
          Created("Good!")
        }).recover {
          case t: Throwable =>
            BadRequest(t.getMessage)
        }
      case None => Future(BadRequest("No integration for this id"))
    }*/

    //todo: implement me!!!
    Future(BadRequest("NYI:("))
  }

  def httpHeaders() = Action.async { implicit request =>
    Future.successful(Ok("headers:" + request.headers.toString() + ";secure:" + RequestUtils.secure))
  }

  def config() = Action.async { implicit request =>
    Future.successful(Ok(Play.current.configuration.underlying.root.render(ConfigRenderOptions.concise())).as("application/json"))
  }
}
