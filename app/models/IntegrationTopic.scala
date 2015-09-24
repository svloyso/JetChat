package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class IntegrationTopic(id: Long = 0, integrationId: String, integrationTopicId: Option[String], integrationGroupId: String, integrationUserId: String,
                            userId: Option[Long], date: Timestamp, text: String) extends AbstractIntegrationMessage

trait IntegrationTopicsComponent extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationUsersComponent with IntegrationGroupsComponent with UsersComponent {
  protected val driver: JdbcProfile

  import driver.api._

  class IntegrationTopicsTable(tag: Tag) extends Table[IntegrationTopic](tag, "integration_topics") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def integrationId = column[String]("integration_id")

    def integrationTopicId = column[Option[String]]("integration_topic_id")

    def integrationGroupId = column[String]("integration_group_id")

    def integrationUserId = column[String]("integration_user_id")

    def userId = column[Option[Long]]("user_id")

    def date = column[Timestamp]("date")

    def text = column[String]("text", O.SqlType("text"))

    def IntegrationTopicIndex = index("integration_topic_index", (integrationId, integrationTopicId))

    def user = foreignKey("integration_topic_user_fk", userId, users)(_.id)

    def integrationGroup = foreignKey("integration_topic_integration_group_fk", (integrationId, integrationGroupId), integrationGroups) (g => (g.integrationId, g.integrationGroupId))

    def integrationUser = foreignKey("integration_topic_integration_user_fk", (integrationId, integrationUserId), integrationUsers)  (u => (u.integrationId, u.integrationUserId))

    def integrationGroupIndex = index("integration_topic_integration_group_index", (integrationId, integrationGroupId), unique = false)

    def integrationUserGroupIndex = index("integration_topic_user_integration_group_index", (integrationId, integrationGroupId, userId), unique = false)

    def * = (id, integrationId, integrationTopicId, integrationGroupId, integrationUserId, userId, date, text) <>(IntegrationTopic.tupled, IntegrationTopic.unapply)
  }

  val integrationTopics = TableQuery[IntegrationTopicsTable]
}

@Singleton()
class IntegrationTopicsDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationTopicsComponent with UsersComponent {

  import driver.api._

  def insert(topic: IntegrationTopic): Future[Long] = {
    db.run((integrationTopics returning integrationTopics.map(_.id)) += topic)
  }

  def find(id: Long): Future[Option[IntegrationTopic]] = {
    db.run(integrationTopics.filter(t => t.id === id).result.headOption)
  }

  def find(integrationId: String, integrationTopicId: String): Future[Option[IntegrationTopic]] = {
    db.run(integrationTopics.filter(t => t.integrationTopicId === integrationTopicId && t.integrationId === integrationId).result.headOption)
  }

  def merge(topic: IntegrationTopic): Future[Boolean] = {
    topic match {
      case t if t.id > 0 =>
        find(topic.id).flatMap {
          case None =>
            db.run(integrationTopics += topic).map(_ => true)
          case Some(existing) =>
            db.run(integrationTopics.filter(t => t.id === existing.id).map(_.integrationTopicId).update(topic.integrationTopicId)).map(_ => false)
        }
      case t if t.integrationTopicId.isDefined =>
        find(topic.integrationId, topic.integrationTopicId.get).flatMap {
        case None =>
          db.run(integrationTopics += topic).map(_ => true)
        case Some(existing) =>
          Future {
            false
          }
      }
      case _ =>
        throw new IllegalStateException()
    }
  }
}
