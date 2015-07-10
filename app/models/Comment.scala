package models

import myUtils.{MyPostgresDriver, WithMyDriver}
import org.joda.time.DateTime

case class Comment(id: Long = 0, groupId: String, topicId: Long, userId: Long, date: DateTime, text: String) extends Message

trait CommentsComponent extends WithMyDriver {

  import driver.simple._

  class CommentsTable(tag: Tag) extends MyPostgresDriver.Table[Comment](tag, "Comment") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def groupId = column[String]("groupId", O.NotNull)
    def topicId = column[Long]("topicId", O.NotNull)
    def userId = column[Long]("userId", O.NotNull)
    def date = column[DateTime]("date", O.NotNull)
    def text = column[String]("text", O.NotNull, O.DBType("text"))

    def group = foreignKey("group_fk", groupId, current.dao.groups)(_.id)
    def topic = foreignKey("topic_fk", topicId, current.dao.topics)(_.id)
    def user = foreignKey("user_fk", userId, current.dao.users)(_.id)

    def * = (id, groupId, topicId, userId, date, text) <>(Comment.tupled, Comment.unapply)
  }

}
