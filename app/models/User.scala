package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// TODO: Make User.name Option[String]
case class User(id: Long = 0, login: String, name: String, avatar: Option[String])

case class UserChat(user: User, text: String, updateDate: Timestamp, unreadCount: Int) extends AbstractChat

trait UsersComponent extends HasDatabaseConfigProvider[JdbcProfile] {
  protected val driver: JdbcProfile
  import driver.api._

  class UsersTable(tag: Tag) extends Table[User](tag, "users") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def login = column[String]("login")
    def name = column[String]("name")
    def avatar = column[Option[String]]("avatar")

    def loginIndex = index("user_login_index", login, unique = true)

    def * = (id, login, name, avatar) <>(User.tupled, User.unapply)
  }

  val users = TableQuery[UsersTable]
}

@Singleton()
class UsersDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with UsersComponent with DirectMessagesComonent {
  import driver.api._

  def findById(id: Long): Future[Option[User]] = {
    db.run(users.filter(_.id === id).result.headOption)
  }

  def findByLogin(login: String): Future[Option[User]] = {
    db.run(users.filter(_.login === login).result.headOption)
  }

  def insert(user: User): Future[Long] = {
    db.run((users returning users.map(_.id)) += user)
  }

  def all: Future[Seq[User]] = {
    db.run(users.result)
  }

  def allWithCounts(userId: Long, nonEmptyOnly: Boolean = false): Future[Seq[UserChat]] = {
    db.run(users.result).flatMap { case u =>
      val allUsers = if (!nonEmptyOnly) u.map(_ ->("", new Timestamp(0), 0, 0)).toMap else Seq().toMap
      db.run(
        (directMessages.filter(_.toUserId === userId)
          join directMessages on { case (directMessage, lastMessage) =>
            lastMessage.date === directMessages.filter(l =>
              (l.toUserId === userId && l.fromUserId === directMessage.fromUserId)
                || (l.fromUserId === userId && l.toUserId === directMessage.toUserId)
            ).map(_.date).max
          } join users on { case ((directMessage, lastMessage), user) => directMessage.fromUserId === user.id }
          joinLeft directMessageReadStatuses on { case (((directMessage, lastMessage), user), status) => directMessage.id === status.directMessageId }
        ).groupBy { case (((directMessage, lastMessage), user), status) =>
          (user.id, user.login, user.name, user.avatar, lastMessage.text)
        }.map { case ((uId, userLogin, userName, userAvatar, text), g) =>
          (uId, userLogin, userName, userAvatar, text, g.map(gg => gg._2.map(_.directMessageId)).length, g.length, g.map(_._1._1._1.date).max)
        }.result
      ).flatMap { d =>
        val messagesSentToUser = d.map { case (uId, userLogin, userName, userAvatar, text, readCount, count, date) =>
          User(uId, userLogin, userName, userAvatar) ->(text, date.get, readCount, count)
        }.toMap
        db.run(
          (directMessages.filter(_.fromUserId === userId)
            join directMessages on { case (directMessage, lastMessage) =>
              lastMessage.date === directMessages.filter(l =>
                (l.toUserId === userId && l.fromUserId === directMessage.fromUserId)
                  || (l.fromUserId === userId && l.toUserId === directMessage.toUserId)
              ).map(_.date).max
            } join users on { case ((directMessage, lastMessage), user) => directMessage.toUserId === user.id }
          ).groupBy { case ((directMessage, lastMessage), user) =>
            (user.id, user.login, user.name, user.avatar, lastMessage.text)
          }.map { case ((uId, userLogin, userName, userAvatar, text), g) =>
            (uId, userLogin, userName, userAvatar, text, 0, 0, g.map(_._1._1.date).max)
          }.result
        ).map { d =>
          val messagesSentByUser = d.map { case (uId, userLogin, userName, userAvatar, text, readCount, count, date) =>
            User(uId, userLogin, userName, userAvatar) ->(text, date.get, readCount, count)
          }.toMap
          (allUsers ++ messagesSentByUser ++ messagesSentToUser).toSeq.sortBy(-_._2._2.getTime)
            .map { case (user, _) =>
              var (textByUser, dateByUser, _, _) = messagesSentByUser.getOrElse(user, ("", new Timestamp(0), 0, 0))
              var (textToUser, dateToUser, readCountToUser, countToUser) = messagesSentToUser.getOrElse(user, ("", new Timestamp(0), 0, 0))
              UserChat(user, if (dateByUser.getTime > dateToUser.getTime) textByUser else textToUser,
                if (dateByUser.getTime > dateToUser.getTime) dateByUser else dateToUser, countToUser - readCountToUser)
            }
        }
      }
    }
  }
}
