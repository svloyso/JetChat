package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class IntegrationUpdate(id: Long = 0, integrationId: String, integrationUpdateId: Option[String], integrationGroupId: String,
                             topicId: Long, integrationUserId: String, userId: Option[Long], date: Timestamp, text: String) extends AbstractIntegrationMessage

trait IntegrationUpdatesComponent extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationUsersComponent with IntegrationGroupsComponent with UsersComponent {
  protected val driver: JdbcProfile

  import driver.api._

  class IntegrationUpdatesTable(tag: Tag) extends Table[IntegrationUpdate](tag, "integration_updates") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def integrationId = column[String]("integration_id")

    def integrationUpdateId = column[Option[String]]("integration_update_id")

    def integrationGroupId = column[String]("integration_group_id")

    def topicId = column[Long]("topic_id")

    def integrationUserId = column[String]("integration_user_id")

    def userId = column[Option[Long]]("user_id")

    def date = column[Timestamp]("date")

    def text = column[String]("text", O.SqlType("text"))

    def IntegrationUpdateIndex = index("integration_update_index", (integrationId, integrationUpdateId))

    def user = foreignKey("integration_update_user_fk", userId, users)(_.id)

    def integrationGroup = foreignKey("integration_update_integration_group_fk", (integrationId, integrationGroupId), integrationGroups) (g => (g.integrationId, g.integrationGroupId))

    def integrationUser = foreignKey("integration_update_integration_user_fk", (integrationId, integrationUserId), integrationUsers)  (u => (u.integrationId, u.integrationUserId))

    def integrationGroupIndex = index("integration_update_integration_group_index", (integrationId, integrationGroupId), unique = false)

    def integrationUserGroupIndex = index("integration_update_user_integration_group_index", (integrationId, integrationGroupId, userId), unique = false)

    def * = (id, integrationId, integrationUpdateId, integrationGroupId, topicId, integrationUserId, userId, date, text) <>(IntegrationUpdate.tupled, IntegrationUpdate.unapply)
  }

  val integrationUpdates = TableQuery[IntegrationUpdatesTable]
}

@Singleton()
class IntegrationUpdatesDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationUpdatesComponent with UsersComponent {

  import driver.api._

  def insert(update: IntegrationUpdate): Future[Long] = {
    db.run((integrationUpdates returning integrationUpdates.map(_.id)) += update)
  }

  def find(id: Long): Future[Option[IntegrationUpdate]] = {
    db.run(integrationUpdates.filter(t => t.id === id).result.headOption)
  }

  def find(integrationId: String, integrationUpdateId: String): Future[Option[IntegrationUpdate]] = {
    db.run(integrationUpdates.filter(t => t.integrationUpdateId === integrationUpdateId && t.integrationId === integrationId).result.headOption)
  }

  def merge(update: IntegrationUpdate): Future[Boolean] = {
    update match {
      case t if t.id > 0 =>
        find(update.id).flatMap {
          case None =>
            db.run(integrationUpdates += update).map(_ => true)
          case Some(existing) =>
            db.run(integrationUpdates.filter(t => t.id === existing.id).map(_.integrationUpdateId).update(update.integrationUpdateId)).map(_ => false)
        }
      case t if t.integrationUpdateId.isDefined =>
        find(update.integrationId, update.integrationUpdateId.get).flatMap {
        case None =>
          db.run(integrationUpdates += update).map(_ => true)
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
