package models

import myUtils.{MyPostgresDriver, WithMyDriver}
import org.joda.time.DateTime

case class Topic(id: Long = 0, groupId: String, userId: Long, date: DateTime, text: String) extends AbstractGroupMessage

trait TopicsComponent extends WithMyDriver {

  import driver.simple._

  class TopicsTable(tag: Tag) extends MyPostgresDriver.Table[Topic](tag, "Topic") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def groupId = column[String]("groupId", O.NotNull)
    def userId = column[Long]("userId", O.NotNull)
    def date = column[DateTime]("date", O.NotNull)
    def text = column[String]("text", O.NotNull, O.DBType("text"))

    def group = foreignKey("group_fk", groupId, current.dao.groups)(_.id)
    def user = foreignKey("user_fk", userId, current.dao.users)(_.id)

    def * = (id, groupId, userId, date, text) <>(Topic.tupled, Topic.unapply)
  }

}
