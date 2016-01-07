package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.Logger


case class IntegrationTopic(integrationId: String, integrationTopicId: String, integrationGroupId: String,
                            userId: Long, integrationUserId: String,
                            date: Timestamp, text: String, title: String) extends AbstractIntegrationMessage

trait IntegrationTopicsComponent extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationUsersComponent with IntegrationGroupsComponent with UsersComponent {
  protected val driver: JdbcProfile

  import driver.api._

  class IntegrationTopicsTable(tag: Tag) extends Table[IntegrationTopic](tag, "integration_topics") {
    def userId = column[Long]("user_id")

    def integrationId = column[String]("integration_id")

    def integrationTopicId = column[String]("integration_topic_id")

    def integrationGroupId = column[String]("integration_group_id")

    def integrationUserId = column[String]("integration_user_id")

    def date = column[Timestamp]("date")

    def text = column[String]("text", O.SqlType("text"))

    def title = column[String]("title")

    def pk = primaryKey("integration_topic_index", (integrationId, integrationTopicId, integrationGroupId, userId))

    def user = foreignKey("integration_token_user_fk", userId, users)(_.id)

    def integrationGroup = foreignKey("integration_topic_integration_group_fk", (integrationId, integrationGroupId, userId), integrationGroups)(g => (g.integrationId, g.integrationGroupId, userId))

    def integrationUser = foreignKey("integration_topic_integration_user_fk", (integrationId, integrationUserId), integrationUsers)(u => (u.integrationId, u.integrationUserId))

    def integrationGroupIndex = index("integration_topic_integration_group_index", (integrationId, integrationGroupId, userId), unique = false)

    def * = (integrationId, integrationTopicId, integrationGroupId, userId, integrationUserId, date, text, title) <>(IntegrationTopic.tupled, IntegrationTopic.unapply)
  }

  val integrationTopics = TableQuery[IntegrationTopicsTable]
}

@Singleton()
class IntegrationTopicsDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationTopicsComponent with IntegrationUpdatesComponent with IntegrationUsersComponent  with UsersComponent {

  import driver.api._

  def find(integrationId: String, integrationGroupId: String, integrationTopicId: String, userId: Long): Future[Option[IntegrationTopic]] = {
    db.run(integrationTopics.filter(t => t.integrationTopicId === integrationTopicId
      && t.integrationGroupId === integrationGroupId
      && t.integrationId === integrationId
      && t.userId === userId).result.headOption)
  }

  def merge(topic: IntegrationTopic): Future[Boolean] = {
    find(topic.integrationId, topic.integrationGroupId, topic.integrationTopicId, topic.userId).flatMap {
      case None =>
        db.run(integrationTopics += topic).map(_ => true)
      case Some(existing) =>
        Future {
          false
        }
    }
  }

  def allWithCounts(userId: Long, integrationId: Option[String], integrationGroupId: Option[String]): Future[Seq[(String, Timestamp, String, String, String, String, String, Option[Long], Option[String], Int)]] = {
    db.run((integrationTopics
      join integrationUsers on { case (topic, integrationUser) => topic.integrationUserId === integrationUser.integrationUserId }
      joinLeft users on { case ((topic, integrationUser), user) => integrationUser.userId === user.id }
      join integrationGroups on { case (((topic, integrationUser), user), group) => topic.integrationGroupId === group.integrationGroupId })
      .filter { case (((topic, integrationUser), user), group) =>
        (integrationId, integrationGroupId) match {
          case (Some(integrationId), Some(integrationGroupId)) => topic.userId === userId &&
            topic.integrationId === integrationId &&
            topic.integrationGroupId === integrationGroupId
          case _ => topic.userId === userId
        }
    }.sortBy(_._1._1._1.date desc).map {
      case (((topic, integrationUser), user), group) =>
        (topic.integrationTopicId, topic.date, topic.text, group.integrationGroupId, group.name, integrationUser.integrationUserId,
          integrationUser.integrationUserName, user.map(_.id), user.map(_.name)) -> 0
    }.result).flatMap { case f =>
      val userTopics = f.toMap

      db.run((integrationTopics joinLeft integrationUpdates on { case (topic, update) => update.integrationTopicId === topic.integrationTopicId }
        join integrationUsers on { case ((topic, update), integrationUser) => topic.integrationUserId === integrationUser.integrationUserId }
        joinLeft users on { case ((topic, integrationUser), user) => integrationUser.userId === user.id }
        join integrationGroups on { case ((((topic, update), integrationUser), user), group) => topic.integrationGroupId === group.integrationGroupId })
        .filter { case ((((topic, update), integrationUser), user), group) =>
          (integrationId, integrationGroupId) match {
            case (Some(integrationId), Some(integrationGroupId)) => topic.integrationId === integrationId && topic.integrationGroupId === integrationGroupId
            case _ => topic.integrationGroupId === topic.integrationGroupId
          }
      }.groupBy { case ((((topic, update), integrationUser), user), group) =>
        (topic.integrationTopicId, topic.date, topic.text, group.integrationGroupId, group.name, integrationUser.integrationUserId,
          integrationUser.integrationUserName, user.map(_.id), user.map(_.name))
      }.map { case ((topicId, topicDate, topicText, gId, groupName, integrationUserId, integrationUserName, uId, userName), g) =>
        (topicId, topicDate, topicText, gId, groupName, integrationUserId, integrationUserName, uId, userName, g.length, g.map(_._1._1._1._1.date).max)
      }.sortBy(_._11 desc).result).map { case f =>
        val commentedTopics = f.map { case (topicId, topicDate, topicText, gId, groupName, integrationUserId, integrationUserName, uId, userName, c, d) =>
          (topicId, topicDate, topicText, gId, groupName, integrationUserId, integrationUserName, uId, userName) -> c
        }.toMap

        val total = (userTopics ++ commentedTopics).toSeq.sortBy(-_._1._2.getTime)

        total.map { case ((topicId, topicDate, topicText, gId, groupName, integrationUserId, integrationUserName, uId, userName), c) =>
          (topicId, topicDate, topicText, gId, groupName, integrationUserId, integrationUserName, uId, userName, c)
        }
      }
    }
  }

}