package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class IntegrationUpdate(integrationId: String, integrationUpdateId: String, integrationGroupId: String,
                             integrationTopicId: String, integrationUserId: String, date: Timestamp, text: String) extends AbstractIntegrationMessage

trait IntegrationUpdatesComponent extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationTopicsComponent with IntegrationUsersComponent with IntegrationGroupsComponent with UsersComponent {
  protected val driver: JdbcProfile

  import driver.api._

  class IntegrationUpdatesTable(tag: Tag) extends Table[IntegrationUpdate](tag, "integration_updates") {
    def integrationId = column[String]("integration_id")

    def integrationUpdateId = column[String]("integration_update_id")

    def integrationGroupId = column[String]("integration_group_id")

    def integrationTopicId = column[String]("integration_topic_id")

    def integrationUserId = column[String]("integration_user_id")

    def date = column[Timestamp]("date")

    def text = column[String]("text", O.SqlType("text"))

    def pk = primaryKey("integration_update_index", (integrationId, integrationUpdateId))

    def integrationGroup = foreignKey("integration_update_integration_group_fk", (integrationId, integrationGroupId), integrationGroups)(g => (g.integrationId, g.integrationGroupId))

    def integrationUser = foreignKey("integration_update_integration_user_fk", (integrationId, integrationUserId), integrationUsers)(u => (u.integrationId, u.integrationUserId))

    def integrationTopic = foreignKey("integration_update_integration_topic_fk", (integrationId, integrationTopicId), integrationTopics)(u => (u.integrationId, u.integrationTopicId))

    def integrationGroupIndex = index("integration_update_integration_group_index", (integrationId, integrationGroupId), unique = false)

    def * = (integrationId, integrationUpdateId, integrationGroupId, integrationTopicId, integrationUserId, date, text) <>(IntegrationUpdate.tupled, IntegrationUpdate.unapply)
  }

  val integrationUpdates = TableQuery[IntegrationUpdatesTable]
}

@Singleton()
class IntegrationUpdatesDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationUpdatesComponent with UsersComponent {

  import driver.api._

  def find(integrationId: String, integrationUpdateId: String): Future[Option[IntegrationUpdate]] = {
    db.run(integrationUpdates.filter(t => t.integrationUpdateId === integrationUpdateId && t.integrationId === integrationId).result.headOption)
  }

  def merge(update: IntegrationUpdate): Future[Boolean] = {
    find(update.integrationId, update.integrationUpdateId).flatMap {
      case None =>
        db.run(integrationUpdates += update).map(_ => true)
      case Some(existing) =>
        Future {
          false
        }
    }
  }
}
