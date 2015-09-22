package models

import javax.inject.{Inject, Singleton}
import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import slick.driver.JdbcProfile
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class IntegrationGroup(integrationId: String, integrationGroupId: String, name: String)

trait IntegrationGroupsComponent extends HasDatabaseConfigProvider[JdbcProfile] {
  protected val driver: JdbcProfile

  import driver.api._

  class IntegrationGroupsTable(tag: Tag) extends Table[IntegrationGroup](tag, "integration_groups") {
    def integrationId = column[String]("integration_id")
    def integrationGroupId = column[String]("integration_group_id")
    def name = column[String]("name")

    def pk = primaryKey("integration_group_pk", (integrationId, integrationGroupId))

    def * = (integrationId, integrationGroupId, name) <>(IntegrationGroup.tupled, IntegrationGroup.unapply)
  }

  val integrationGroups = TableQuery[IntegrationGroupsTable]
}

@Singleton()
class IntegrationGroupsDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationGroupsComponent {

  import driver.api._

  def find(integrationId: String, integrationGroupId: String): Future[Option[IntegrationGroup]] = {
    db.run(integrationGroups.filter(g => g.integrationGroupId === integrationGroupId && g.integrationId === integrationId).result.headOption)
  }

  def merge(group: IntegrationGroup): Future[Boolean] = {
    find(group.integrationId, group.integrationGroupId).flatMap {
      case None =>
        db.run(integrationGroups += group).map(_ => true)
      case Some(existing) =>
        Future {
          false
        }
    }
  }
}