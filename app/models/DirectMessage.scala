package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class DirectMessage(id: Long = 0, fromUserId: Long, toUserId: Long, date: Timestamp, text: String) extends AbstractMessage

case class DirectMessageReadStatus(directMessageId: Long)

case class LastDirectMessage(minUserId: Long, maxUserId: Long, date: Timestamp, text: String, directMessageId: Long)

trait DirectMessagesComonent extends HasDatabaseConfigProvider[JdbcProfile] with UsersComponent {
  protected val driver: JdbcProfile

  import driver.api._

  class DirectMessagesTable(tag: Tag) extends Table[DirectMessage](tag, "direct_messages") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def fromUserId = column[Long]("from_user_id")

    def toUserId = column[Long]("to_user_id")

    def date = column[Timestamp]("date")

    def text = column[String]("text", O.SqlType("text"))

    def fromUser = foreignKey("dm_from_user_fk", fromUserId, users)(_.id)

    def toUser = foreignKey("dm_to_user_fk", toUserId, users)(_.id)

      def * = (id, fromUserId, toUserId, date, text) <>(DirectMessage.tupled, DirectMessage.unapply)
  }

  val directMessages = TableQuery[DirectMessagesTable]

  class DirectMessageReadStatusesTable(tag: Tag) extends Table[DirectMessageReadStatus](tag, "direct_message_read_statuses") {
    def directMessageId = column[Long]("direct_message_id", O.PrimaryKey)

    def directMessage = foreignKey("direct_message_read_status_direct_message_fk", directMessageId, directMessages)(_.id)

    def * = directMessageId <>(DirectMessageReadStatus.apply, DirectMessageReadStatus.unapply)
  }

  val directMessageReadStatuses = TableQuery[DirectMessageReadStatusesTable]

  class LastDirectMessagesTable(tag: Tag) extends Table[LastDirectMessage](tag, "last_direct_messages") {
    def minUserId = column[Long]("min_user_id")

    def maxUserId = column[Long]("max_user_id")

    def date = column[Timestamp]("date")

    def text = column[String]("text", O.SqlType("text"))

    def directMessageId = column[Long]("direct_message_id")

    def minUser = foreignKey("last_direct_message_min_user_fk", minUserId, users)(_.id)

    def maxUser = foreignKey("last_direct_message_max_user_fk", maxUserId, users)(_.id)

    def directMessage = foreignKey("last_direct_message_direct_message_fk", directMessageId, directMessages)(_.id)

    def pk = primaryKey("last_direct_message_pk", (minUserId, maxUserId))

    def * = (minUserId, maxUserId, date, text, directMessageId) <>(LastDirectMessage.tupled, LastDirectMessage.unapply)
  }

  val lastDirectMessages = TableQuery[LastDirectMessagesTable]
}

@Singleton()
class DirectMessagesDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with DirectMessagesComonent {

  import driver.api._

  def insert(directMessage: DirectMessage): Future[Long] = {
    db.run((directMessages returning directMessages.map(_.id)) += directMessage).flatMap( id =>
      db.run(lastDirectMessages.insertOrUpdate(LastDirectMessage(
        Math.min(directMessage.fromUserId, directMessage.toUserId),
        Math.max(directMessage.fromUserId, directMessage.toUserId),
        directMessage.date,
        directMessage.text,
        id
      ))).map(_ => id)
    )
  }

  def messages(fromUserId: Long, toUserId: Long): Future[Seq[(DirectMessage, Boolean, User, User)]] = {
    // TODO: Change to Seq[DirectMessage]
    db.run((for {
      (((m, status), fromUser), toUser) <- directMessages.filter(m =>
        (m.fromUserId === fromUserId && m.toUserId === toUserId) ||
          (m.fromUserId === toUserId && m.toUserId === fromUserId)) joinLeft directMessageReadStatuses on { case (message, status) => message.id === status.directMessageId } join users on { case ((m, status), fromUser) => m.fromUserId === fromUser.id } join users on { case (((m, status), fromUser), toUser) => m.toUserId === toUser.id }
    } yield (m, status.map(_.directMessageId).isDefined, fromUser, toUser)).sortBy(_._1.date).result)
  }

  def markAsRead(directMessageIds: Seq[Long]): Future[Option[Int]] = {
    db.run(directMessageReadStatuses ++= directMessageIds.map(DirectMessageReadStatus))
  }
}
