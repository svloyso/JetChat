package models

import javax.inject.{Inject, Singleton}
import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import slick.driver.JdbcProfile
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class IntegrationGroup(integrationId: String, integrationGroupId: String, userId: Long, name: String)

trait IntegrationGroupsComponent extends HasDatabaseConfigProvider[JdbcProfile] with UsersComponent {
  protected val driver: JdbcProfile

  import driver.api._

  class IntegrationGroupsTable(tag: Tag) extends Table[IntegrationGroup](tag, "integration_groups") {
    def userId = column[Long]("user_id")

    def integrationId = column[String]("integration_id")

    def integrationGroupId = column[String]("integration_group_id")

    def name = column[String]("name")

    def pk = primaryKey("integration_group_pk", (integrationId, integrationGroupId, userId))

    def user = foreignKey("integration_group_user_fk", userId, users)(_.id)

    def * = (integrationId, integrationGroupId, userId, name) <> (IntegrationGroup.tupled, IntegrationGroup.unapply)
  }

  val integrationGroups = TableQuery[IntegrationGroupsTable]
}

@Singleton()
class IntegrationGroupsDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationGroupsComponent
  with IntegrationTopicsComponent with IntegrationUpdatesComponent {

  import driver.api._

  def find(integrationId: String, integrationGroupId: String, userId: Long): Future[Option[IntegrationGroup]] = {
    db.run(integrationGroups.filter(g => g.integrationGroupId === integrationGroupId &&
      g.integrationId === integrationId && g.userId === userId).result.headOption)
  }

  def merge(group: IntegrationGroup): Future[Boolean] = {
    find(group.integrationId, group.integrationGroupId, group.userId).flatMap {
      case None =>
        db.run(integrationGroups += group).map(_ => true)
      case Some(existing) =>
        Future {
          false
        }
    }
  }

  def allWithCounts(userId: Long, query: Option[String]): Future[Seq[(IntegrationGroup, Int)]] = {
    db.run(integrationGroups.filter(_.userId === userId).result).flatMap { f =>
      val groupMap = f.map { g => (g.integrationId, g.integrationGroupId) ->(g.name, 0) }.toMap

      db.run((integrationTopics.filter(_.userId === userId)
        join integrationGroups on { case (topic, group) => topic.integrationGroupId === group.integrationGroupId && topic.integrationId === group.integrationId })
        .groupBy { case (topic, group) => (group.integrationId, group.integrationGroupId, group.name) }
        .map { case ((integrationId, integrationGroupId, groupName), g) => (integrationId, integrationGroupId, groupName, g.length) }.result)
        .flatMap { f =>
          val topicMap = f.map { case (integrationId, integrationGroupId, groupName, count) =>
            (integrationId, integrationGroupId) ->(groupName, count)
          }.toMap

          db.run((integrationUpdates.filter(_.userId === userId)
            join integrationGroups on { case (update, group) => update.integrationGroupId === group.integrationGroupId && update.integrationId === group.integrationId })
            .groupBy { case (update, group) => (group.integrationId, group.integrationGroupId, group.name) }
            .map { case ((integrationId, integrationGroupId, groupName), g) => (integrationId, integrationGroupId, groupName, g.length) }.result)
            .map { f =>
              val updateMap = f.map { case (integrationId, integrationGroupId, groupName, count) =>
                (integrationId, integrationGroupId) ->(groupName, count)
              }.toMap

              val groupTotal = groupMap ++ (groupMap ++ topicMap.map { case ((integrationId, integrationGroupId), groupTopicToken) =>
                val groupUpdateMap = updateMap.getOrElse((integrationId, integrationGroupId), (groupTopicToken._1, 0))
                (integrationId, integrationGroupId) -> (groupTopicToken._1 -> (groupTopicToken._2 + groupUpdateMap._2))
              })

              groupTotal.toSeq.sortBy(a => a._2._2).map { case ((integrationId, integrationGroupId), token) =>
                (IntegrationGroup(integrationId, integrationGroupId, userId, token._1), token._2)
              }
            }
        }
    }
  }

}