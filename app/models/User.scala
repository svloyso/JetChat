package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// TODO: Make User.name Option[String]
case class User(id: Long = 0, login: String, name: String, avatar: Option[String], email: Option[String])

case class UserChat(user: User, text: String, updateDate: Timestamp, unreadCount: Int) extends AbstractChat

trait UsersComponent extends HasDatabaseConfigProvider[JdbcProfile] {
  protected val driver: JdbcProfile
  import driver.api._

  class UsersTable(tag: Tag) extends Table[User](tag, "users") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def login = column[String]("login")
    def name = column[String]("name")
    def avatar = column[Option[String]]("avatar")
    def email = column[Option[String]]("email")
    def loginIndex = index("user_login_index", login, unique = true)
    def * = (id, login, name, avatar, email) <> (User.tupled, User.unapply)
  }

  val allUsers = TableQuery[UsersTable]
}

@Singleton()
class UsersDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with UsersComponent with DirectMessagesComonent {
  import driver.api._

  def findById(id: Long): Future[Option[User]] = {
    db.run(allUsers.filter(_.id === id).result.headOption)
  }

  def findByLogin(login: String): Future[Option[User]] = {
    db.run(allUsers.filter(_.login === login).result.headOption)
  }

  def insert(user: User): Future[Long] = {
    db.run((allUsers returning allUsers.map(_.id)) += user)
  }

  def mergeByLogin(login: String, name: String, avatar: Option[String] = None): Future[User] = {
    findByLogin(login).flatMap {
      case None =>
        val user: User = User(login = login, name = name, avatar = avatar)
        insert(user).map { id => User(id, user.login, user.name, user.avatar) }
      case Some(user) =>
        Future(user)
    }

  def update(user: User): Future[Boolean] = {
    db.run(allUsers.filter(_.id === user.id).map(u => (u.avatar, u.email)).update(user.avatar, user.email)).map(_ > 0)
  }

  def all: Future[Seq[User]] = {
    db.run(allUsers.result)
  }

  def allWithCounts(userId: Long, nonEmptyOnly: Boolean = false, query: Option[String]): Future[Seq[UserChat]] = {
    db.run(allUsers.result).flatMap { case u =>
      val allUsers = if (!nonEmptyOnly) u.map(_ ->("", new Timestamp(0), 0, 0)).toMap else Seq().toMap
      val sqlQuery = query match {
        case None =>
          sql"""SELECT u.id, u.login, u.name, u.avatar, u.email, ld.date, ld.text,
            sum(CASE WHEN ds.direct_message_id IS NOT NULL AND d.to_user_id = $userId THEN 1 ELSE 0 END) read_count,
            sum(CASE WHEN d.to_user_id = $userId THEN 1 ELSE 0 END) count
            FROM direct_messages d
            LEFT JOIN users u ON u.id = d.to_user_id OR u.id = d.from_user_id
            LEFT JOIN direct_message_read_statuses ds ON ds.direct_message_id = d.id
            LEFT JOIN last_direct_messages ld ON ld.min_user_id = LEAST($userId, u.id) AND ld.max_user_id = GREATEST($userId, u.id)
            WHERE (d.from_user_id = $userId OR d.to_user_id = $userId) AND u.id <> $userId
            GROUP BY u.id, u.login, u.name, u.avatar, ld.date, ld.text
            ORDER BY date DESC"""
        case Some(words) =>
          sql"""SELECT u.id, u.login, u.name, u.avatar, u.email, ld.date, ld.text,
            sum(CASE WHEN ds.direct_message_id IS NOT NULL AND d.to_user_id = $userId THEN 1 ELSE 0 END) read_count,
            sum(CASE WHEN d.to_user_id = $userId THEN 1 ELSE 0 END) count
            FROM direct_messages d
            LEFT JOIN users u ON u.id = d.to_user_id OR u.id = d.from_user_id
            LEFT JOIN direct_message_read_statuses ds ON ds.direct_message_id = d.id
            LEFT JOIN last_direct_messages ld ON ld.min_user_id = LEAST($userId, u.id) AND ld.max_user_id = GREATEST($userId, u.id)
            LEFT JOIN direct_messages dm ON LEAST(dm.from_user_id, dm.to_user_id) = LEAST($userId, u.id)
              AND GREATEST(dm.from_user_id, dm.to_user_id) = GREATEST($userId, u.id)
            WHERE locate($words, dm.text) >= 1 AND (d.from_user_id = $userId OR d.to_user_id = $userId) AND u.id <> $userId
            GROUP BY u.id, u.login, u.name, u.avatar, ld.date, ld.text
            ORDER BY date DESC"""
      }

      db.run(
        sqlQuery.as[(Long, String, String, Option[String], Option[String], Timestamp, String, Int, Int)]
      ).map { case results =>
        val myMessages = results.map { case (id, login, name, avatar, email, updateDate, text, readCount, totalCount) =>
          User(id, login, name, avatar, email) ->(text, updateDate, readCount, totalCount)
        }.toMap
        (allUsers ++ myMessages).toSeq.sortBy(-_._2._2.getTime).map { case (user, (text, updateDate, readCount, totalCount)) =>
          UserChat(user, text, updateDate, totalCount - readCount)
        }
      }
    }
  }
}
