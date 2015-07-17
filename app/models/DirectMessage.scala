package models

import org.joda.time.DateTime

case class DirectMessage(id: Long = 0, fromUserId: Long, toUserId: Long, date: DateTime, text: String) extends AbstractMessage

trait DirectMessagesComponent extends WithMyDriver {

  import driver.simple._
  import com.github.tototoshi.slick.MySQLJodaSupport._

  class DirectMessagesTable(tag: Tag) extends CustomDriver.Table[DirectMessage](tag, "direct_messages") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def fromUserId = column[Long]("from_user_id", O.NotNull)
    def toUserId = column[Long]("to_user_id", O.NotNull)
    def date = column[DateTime]("date", O.NotNull)
    def text = column[String]("text", O.NotNull, O.DBType("text"))

    def fromUser = foreignKey("dm_from_user_fk", fromUserId, current.dao.users)(_.id)
    def toUser = foreignKey("dm_to_user_fk", toUserId, current.dao.users)(_.id)

    def * = (id, fromUserId, toUserId, date, text) <>(DirectMessage.tupled, DirectMessage.unapply)
  }

}
