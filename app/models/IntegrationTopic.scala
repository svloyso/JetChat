package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class IntegrationTopic(integrationId: String, integrationTopicId: String, integrationGroupId: String, integrationUserId: String,
                            date: Timestamp, text: String) extends AbstractIntegrationMessage

trait IntegrationTopicsComponent extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationUsersComponent with IntegrationGroupsComponent with UsersComponent {
  protected val driver: JdbcProfile

  import driver.api._

  class IntegrationTopicsTable(tag: Tag) extends Table[IntegrationTopic](tag, "integration_topics") {
    def integrationId = column[String]("integration_id")

    def integrationTopicId = column[String]("integration_topic_id")

    def integrationGroupId = column[String]("integration_group_id")

    def integrationUserId = column[String]("integration_user_id")

    def date = column[Timestamp]("date")

    def text = column[String]("text", O.SqlType("text"))

    def pk = primaryKey("integration_topic_index", (integrationId, integrationTopicId))

    def integrationGroup = foreignKey("integration_topic_integration_group_fk", (integrationId, integrationGroupId), integrationGroups)(g => (g.integrationId, g.integrationGroupId))

    def integrationUser = foreignKey("integration_topic_integration_user_fk", (integrationId, integrationUserId), integrationUsers)(u => (u.integrationId, u.integrationUserId))

    def integrationGroupIndex = index("integration_topic_integration_group_index", (integrationId, integrationGroupId), unique = false)

    def * = (integrationId, integrationTopicId, integrationGroupId, integrationUserId, date, text) <>(IntegrationTopic.tupled, IntegrationTopic.unapply)
  }

  val integrationTopics = TableQuery[IntegrationTopicsTable]
}

@Singleton()
class IntegrationTopicsDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationTopicsComponent with UsersComponent {

  import driver.api._

  def find(integrationId: String, integrationTopicId: String): Future[Option[IntegrationTopic]] = {
    db.run(integrationTopics.filter(t => t.integrationTopicId === integrationTopicId && t.integrationId === integrationId).result.headOption)
  }

  def merge(topic: IntegrationTopic): Future[Boolean] = {
    find(topic.integrationId, topic.integrationTopicId).flatMap {
      case None =>
        db.run(integrationTopics += topic).map(_ => true)
      case Some(existing) =>
        Future {
          false
        }
    }
  }
}
