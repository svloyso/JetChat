package models

import org.joda.time.DateTime

case class Comment(id: Long = 0, groupId: Long, topicId: Long, userId: Long, date: DateTime, text: String) extends
AbstractGroupMessage

trait CommentsComponent extends WithMyDriver {

  import driver.simple._
  import com.github.tototoshi.slick.MySQLJodaSupport._

  class CommentsTable(tag: Tag) extends CustomDriver.Table[Comment](tag, "comments") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def groupId = column[Long]("group_id", O.NotNull)
    def topicId = column[Long]("topic_id", O.NotNull)
    def userId = column[Long]("user_id", O.NotNull)
    def date = column[DateTime]("date", O.NotNull)
    def text = column[String]("text", O.NotNull, O.DBType("text"))

    def group = foreignKey("comment_group_fk", groupId, current.dao.groups)(_.id)
    def topic = foreignKey("comment_topic_fk", topicId, current.dao.topics)(_.id)
    def user = foreignKey("comment_user_fk", userId, current.dao.users)(_.id)

    def * = (id, groupId, topicId, userId, date, text) <>(Comment.tupled, Comment.unapply)
  }

}
