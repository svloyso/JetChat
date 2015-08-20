package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.Future

case class DirectMessage(id: Long = 0, fromUserId: Long, toUserId: Long, date: Timestamp, text: String) extends AbstractMessage

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
}

@Singleton()
class DirectMessagesDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with DirectMessagesComonent {

  import driver.api._

  def insert(directMessage: DirectMessage): Future[Long] = {
    db.run((directMessages returning directMessages.map(_.id)) += directMessage)
  }

  def messages(fromUserId: Long, toUserId: Long): Future[Seq[(DirectMessage, User, User)]] = {
    // TODO: Change to Seq[DirectMessage]
    db.run((for {
      ((m, fromUser), toUser) <- directMessages.filter(m =>
        (m.fromUserId === fromUserId && m.toUserId === toUserId) ||
          (m.fromUserId === toUserId && m.toUserId === fromUserId)) join users on { case (m, fromUser) => m.fromUserId === fromUser.id } join users on { case ((m, fromUser), toUser) => m.toUserId === toUser.id }
    } yield (m, fromUser, toUser)).sortBy(_._1.date).result)
  }
}
