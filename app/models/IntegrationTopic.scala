package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


case class IntegrationTopic(id: Long = 0, integrationId: String, integrationTopicId: Option[String], integrationGroupId: String,
                            userId: Long, integrationUserId: String,
                            date: Timestamp, text: String, title: String) extends AbstractIntegrationMessage

object IntegrationTopicFactory {
  def applyNoOption(id: Long = 0, integrationId: String, integrationTopicId: String, integrationGroupId: String,
            userId: Long, integrationUserId: String,
            date: Timestamp, text: String, title: String): IntegrationTopic = {
    IntegrationTopic(id, integrationId, Option(integrationTopicId), integrationGroupId, userId, integrationUserId, date, text, title)
  }

  def unapplyNoOption(topic: IntegrationTopic): Option[(Long, String, String, String, Long, String, Timestamp, String, String)] = {
    IntegrationTopic.unapply(topic).map {
      case (id: Long, integrationId: String, integrationTopicId: Option[String], integrationGroupId: String,
        userId: Long, integrationUserId: String,
        date: Timestamp, text: String, title: String) =>
        (id, integrationId, integrationTopicId.orNull, integrationGroupId, userId, integrationUserId, date, text, title)
    }
  }
}

trait IntegrationTopicsComponent extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationUsersComponent with IntegrationGroupsComponent with UsersComponent {
  protected val driver: JdbcProfile

  import driver.api._

  class IntegrationTopicsTable(tag: Tag) extends Table[IntegrationTopic](tag, "integration_topics") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def integrationId = column[String]("integration_id")
    def integrationTopicId = column[Option[String]]("integration_topic_id")
    def integrationGroupId = column[String]("integration_group_id")
    def integrationUserId = column[String]("integration_user_id")
    def date = column[Timestamp]("date")
    def text = column[String]("text", O.SqlType("text"))
    def title = column[String]("title")
    def integrationTopicIndex = index("integration_topics_index", (integrationId, integrationTopicId, integrationGroupId, userId))
    def user = foreignKey("integration_token_user_fk", userId, allUsers)(_.id)
    def * = (id, integrationId, integrationTopicId, integrationGroupId, userId, integrationUserId, date, text, title) <> (IntegrationTopic.tupled, IntegrationTopic.unapply)
  }

  val allIntegrationTopics = TableQuery[IntegrationTopicsTable]

  def integrationTopicsByQuery(
    query: Option[String],
    updatesChecker: Function2[Option[String], Rep[Option[String]], Rep[Boolean]],
    topics: Query[IntegrationTopicsTable, IntegrationTopic, Seq] = allIntegrationTopics.to[Seq])
  = {
    query match {
      case Some(words) => topics filter {
        topic => topic.text.indexOf(words) >= 0 ||
          topic.integrationTopicId.indexOf(words) >= 0 ||
          topic.integrationUserId.indexOf(words) >= 0 ||
          updatesChecker(query, topic.integrationTopicId)
      }
      case None => topics
    }
  }
}

@Singleton()
class IntegrationTopicsDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile]
    with IntegrationTopicsComponent
    with IntegrationUpdatesComponent
    with IntegrationUsersComponent
    with UsersComponent {

  import driver.api._

  def find(integrationId: String, integrationGroupId: String, integrationTopicId: String, userId: Long): Future[Option[IntegrationTopic]] = {
    db.run(allIntegrationTopics.filter(t => t.integrationTopicId === integrationTopicId
      && t.integrationGroupId === integrationGroupId
      && t.integrationId === integrationId
      && t.userId === userId).result.headOption)
  }

  def find(id: Long): Future[Option[IntegrationTopic]] = {
    db.run(allIntegrationTopics.filter(t => t.id === id).result.headOption)
  }

  def insert(update: IntegrationTopic): Future[Long] = {
    db.run((allIntegrationTopics returning allIntegrationTopics.map(_.id)) += update)
  }

  def merge(topic: IntegrationTopic): Future[Boolean] = {
    if (topic.id > 0) {
      find(topic.id).flatMap {
        case None => db.run(allIntegrationTopics += topic).map(_ => true)
        case Some(existing) =>
          db.run(allIntegrationTopics.filter(u => u.id === topic.id)
            .map(_.integrationTopicId).update(topic.integrationTopicId)).map(_ => false)
      }
    } else if (topic.integrationTopicId.isDefined) {
      find(topic.integrationId, topic.integrationGroupId, topic.integrationTopicId.get, topic.userId).flatMap {
        case None => db.run(allIntegrationTopics += topic).map(_ => true)
        case Some(existing) =>
          db.run(allIntegrationTopics.filter(u => u.integrationTopicId === topic.integrationTopicId.get &&
            u.integrationId === topic.integrationId && u.integrationGroupId === topic.integrationGroupId && u.userId === topic.userId)
            .map(_.integrationTopicId).update(topic.integrationTopicId)).map(_ => false)
      }
    } else {
      throw new IllegalArgumentException
    }
  }

  def allWithCounts(
    userId: Long,
    integrationId: Option[String],
    integrationGroupId: Option[String],
    query: Option[String]): Future[Seq[(String, Option[String], Timestamp, String, String, String, String, String, Option[Long], Option[String], Int)]]
  = {

    val integrationTopics = integrationTopicsByQuery(query, updatesByQueryAndTopicId(_, _).exists)
    val updates = updatesByQuery(query)

    db.run((integrationTopics
      join allIntegrationUsers on { case (topic, integrationUser) => topic.integrationUserId === integrationUser.integrationUserId }
      joinLeft allUsers on { case ((topic, integrationUser), user) => integrationUser.userId === user.id }
      join allIntegrationGroups on { case (((topic, integrationUser), user), group) => topic.integrationGroupId === group.integrationGroupId })
      .filter { case (((topic, integrationUser), user), group) =>
        (integrationId, integrationGroupId) match {
          case (Some(integrationId), Some(integrationGroupId)) => topic.userId === userId &&
            topic.integrationId === integrationId &&
            topic.integrationGroupId === integrationGroupId
          case _ => topic.userId === userId
        }
    }.sortBy(_._1._1._1.date desc).map {
      case (((topic, integrationUser), user), group) =>
        (topic.integrationId, topic.integrationTopicId, topic.date, topic.text, group.integrationGroupId, group.name, integrationUser.integrationUserId,
          integrationUser.integrationUserName, user.map(_.id), user.map(_.name)) -> 0
    }.result).flatMap { case f =>
      val userTopics = f.toMap

      db.run((integrationTopics joinLeft allIntegrationUpdates on { case (topic, update) => update.integrationTopicId === topic.integrationTopicId }
        join allIntegrationUsers on { case ((topic, update), integrationUser) => topic.integrationUserId === integrationUser.integrationUserId }
        joinLeft allUsers on { case ((topic, integrationUser), user) => integrationUser.userId === user.id }
        join allIntegrationGroups on { case ((((topic, update), integrationUser), user), group) => topic.integrationGroupId === group.integrationGroupId })
        .filter { case ((((topic, update), integrationUser), user), group) =>
          (integrationId, integrationGroupId) match {
            case (Some(integrationId), Some(integrationGroupId)) => topic.userId === userId &&
              topic.integrationId === integrationId &&
              topic.integrationGroupId === integrationGroupId
            case _ => topic.userId === userId
          }
      }.groupBy { case ((((topic, update), integrationUser), user), group) =>
        (topic.integrationId, topic.integrationTopicId, topic.date, topic.text, group.integrationGroupId, group.name, integrationUser.integrationUserId,
          integrationUser.integrationUserName, user.map(_.id), user.map(_.name))
      }.map { case ((topicIntegrationId, topicId, topicDate, topicText, gId, groupName, integrationUserId, integrationUserName, uId, userName), g) =>
        (topicIntegrationId, topicId, topicDate, topicText, gId, groupName, integrationUserId, integrationUserName, uId, userName, g.length, g.map(_._1._1._1._1.date).max)
      }.sortBy(_._12 desc).result).map { case f =>
        val commentedTopics = f.map { case (topicIntegrationId, topicId, topicDate, topicText, gId, groupName, integrationUserId, integrationUserName, uId, userName, c, d) =>
          (topicIntegrationId, topicId, topicDate, topicText, gId, groupName, integrationUserId, integrationUserName, uId, userName) -> c
        }.toMap

        val total = (userTopics ++ commentedTopics).toSeq.sortBy(-_._1._3.getTime)

        total.map { case ((topicIntegrationId, topicId, topicDate, topicText, gId, groupName, integrationUserId, integrationUserName, uId, userName), c) =>
          (topicIntegrationId, topicId, topicDate, topicText, gId, groupName, integrationUserId, integrationUserName, uId, userName, c)
        }
      }
    }
  }

  def messages(
      userId: Long,
      integrationId: String,
      integrationGroupId: String,
      integrationTopicId: String,
      query: Option[String]): Future[Seq[(AbstractIntegrationMessage, IntegrationUser, IntegrationGroup)]]
  = {
    val integrationTopics = integrationTopicsByQuery(query, updatesByQueryAndTopicId(_, _).exists)
    val integrationUpdates = updatesByQuery(query)

    db.run((integrationTopics .filter(t => t.userId === userId && t.integrationId === integrationId &&
        t.integrationGroupId === integrationGroupId && t.integrationTopicId === integrationTopicId)
      join allIntegrationUsers on { case (topic, user) => user.integrationId === integrationId &&
        user.integrationUserId === topic.integrationUserId }
      join allIntegrationGroups on { case ((topic, user), group) => group.userId === userId &&
        group.integrationId === integrationId && group.integrationGroupId === topic.integrationGroupId})
      .map { case ((topic, user), group) => (topic, user, group) }.result
    ).flatMap { case t =>
      db.run((integrationUpdates.filter(u => u.userId === userId && u.integrationId === integrationId
        && u.integrationGroupId === integrationGroupId && u.integrationTopicId === integrationTopicId)
        join allIntegrationUsers on { case (update, user) => user.integrationId === integrationId &&
          user.integrationUserId === update.integrationUserId }
        join allIntegrationGroups on { case ((update, user), group) => group.userId === userId &&
        group.integrationId === integrationId && group.integrationGroupId === update.integrationGroupId})
        .map { case ((update, user), group) =>
          (update, user, group)
        }.sortBy(_._1.date).result
      ).map { case f =>
        f.++:(t)
      }
    }
  }
}
