package models

import org.joda.time.DateTime

case class Topic(id: Long = 0, groupId: Long, userId: Long, date: DateTime, text: String) extends AbstractGroupMessage

trait TopicsComponent extends WithMyDriver {

  import driver.simple._
  import com.github.tototoshi.slick.MySQLJodaSupport._

  class TopicsTable(tag: Tag) extends CustomDriver.Table[Topic](tag, "topics") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def groupId = column[Long]("group_id", O.NotNull)
    def userId = column[Long]("user_id", O.NotNull)
    def date = column[DateTime]("date", O.NotNull)
    def text = column[String]("text", O.NotNull, O.DBType("text"))

    def group = foreignKey("topic_group_fk", groupId, current.dao.groups)(_.id)
    def user = foreignKey("topic_user_fk", userId, current.dao.users)(_.id)

    def * = (id, groupId, userId, date, text) <>(Topic.tupled, Topic.unapply)
  }

}
