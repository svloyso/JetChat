package models

import myUtils.{MyPostgresDriver, WithMyDriver}
import org.joda.time.DateTime

case class DirectMessage(id: Long = 0, fromUserId: Long, toUserId: Long, date: DateTime, text: String) extends AbstractMessage

trait DirectMessagesComponent extends WithMyDriver {

  import driver.simple._

  class DirectMessagesTable(tag: Tag) extends MyPostgresDriver.Table[DirectMessage](tag, "DirectMessage") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def fromUserId = column[Long]("fromUserId", O.NotNull)
    def toUserId = column[Long]("toUserId", O.NotNull)
    def date = column[DateTime]("date", O.NotNull)
    def text = column[String]("text", O.NotNull, O.DBType("text"))

    def fromUser = foreignKey("from_user_fk", fromUserId, current.dao.users)(_.id)
    def toUser = foreignKey("to_user_fk", toUserId, current.dao.users)(_.id)

    def * = (id, fromUserId, toUserId, date, text) <>(DirectMessage.tupled, DirectMessage.unapply)
  }

}
