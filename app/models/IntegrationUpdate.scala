package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.Logger

case class IntegrationUpdate(id: Long, integrationId: String, integrationUpdateId: Option[String], integrationGroupId: String,
                             integrationTopicId: String, userId: Long,
                             integrationUserId: String, date: Timestamp, text: String) extends AbstractIntegrationMessage

trait IntegrationUpdatesComponent extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationTopicsComponent with IntegrationUsersComponent with IntegrationGroupsComponent with UsersComponent {
  protected val driver: JdbcProfile

  import driver.api._

  class IntegrationUpdatesTable(tag: Tag) extends driver.api.Table[IntegrationUpdate](tag, "integration_updates") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def integrationId = column[String]("integration_id")
    def integrationUpdateId = column[Option[String]]("integration_update_id")
    def integrationGroupId = column[String]("integration_group_id")
    def integrationTopicId = column[String]("integration_topic_id")
    def integrationUserId = column[String]("integration_user_id")
    def date = column[Timestamp]("date")
    def text = column[String]("text", O.SqlType("text"))
    // Note, this is supposed to be a unique index. It's not because integrationUpdateId is nullable
    def integrationUpdateIndex = index("integration_update_index", (integrationId, integrationUpdateId, userId), unique = false)
    def integrationGroup = foreignKey("integration_update_integration_group_fk", (integrationId, integrationGroupId, userId), allIntegrationGroups)(g => (g.integrationId, g.integrationGroupId, g.userId))
    def integrationUser = foreignKey("integration_update_integration_user_fk", (integrationId, integrationUserId), allIntegrationUsers)(u => (u.integrationId, u.integrationUserId))
    def integrationTopic = foreignKey("integration_update_integration_topic_fk", (integrationId, integrationTopicId, integrationGroupId, userId), allIntegrationTopics)(u => (u.integrationId, u.integrationTopicId, u.integrationGroupId, u.userId))
    def integrationGroupIndex = index("integration_update_integration_group_index", (integrationId, integrationGroupId, userId), unique = false)
    def * = (id, integrationId, integrationUpdateId, integrationGroupId, integrationTopicId, userId, integrationUserId, date, text) <> (IntegrationUpdate.tupled, IntegrationUpdate.unapply)
  }

  val allIntegrationUpdates = TableQuery[IntegrationUpdatesTable]
}

@Singleton()
class IntegrationUpdatesDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationUpdatesComponent with UsersComponent {

  import driver.api._

  def find(integrationId: String, integrationGroupId: String, integrationUpdateId: String, userId: Long): Future[Option[IntegrationUpdate]] = {
    db.run(allIntegrationUpdates.filter(u => u.integrationUpdateId === integrationUpdateId &&
      u.integrationId === integrationId && u.integrationGroupId === integrationGroupId && u.userId === userId).result.headOption)
  }

  def find(id: Long): Future[Option[IntegrationUpdate]] = {
    db.run(allIntegrationUpdates.filter(t => t.id === id).result.headOption)
  }

  def insert(update: IntegrationUpdate): Future[Long] = {
    db.run((allIntegrationUpdates returning allIntegrationUpdates.map(_.id)) += update)
  }

  def merge(update: IntegrationUpdate): Future[Boolean] = {
    if (update.id > 0) {
      find(update.id).flatMap {
        case None =>
          db.run(allIntegrationUpdates += update).map(_ => true)
        case Some(existing) =>
          db.run(allIntegrationUpdates.filter(u => u.id === update.id)
            .map(_.integrationUpdateId).update(update.integrationUpdateId)).map(_ => false)
      }
    } else if (update.integrationUpdateId.isDefined) {
      find(update.integrationId, update.integrationGroupId, update.integrationUpdateId.get, update.userId).flatMap {
        case None =>
          db.run(allIntegrationUpdates += update).map(_ => true)
        case Some(existing) =>
          db.run(allIntegrationUpdates.filter(u => u.integrationUpdateId === update.integrationUpdateId.get &&
            u.integrationId === update.integrationId && u.integrationGroupId === update.integrationGroupId && u.userId === update.userId)
            .map(_.integrationUpdateId).update(update.integrationUpdateId)).map(_ => false)
      }
    } else {
      throw new IllegalArgumentException
    }
  }
}
