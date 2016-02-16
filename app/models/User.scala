package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// TODO: Make User.name Option[String]
case class User(id: Long = 0, login: String, name: String, avatar: Option[String])

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

  def allWithCounts(userId: Long): Future[Seq[(User, Int, Int)]] = {
    db.run(users.result).flatMap { case u =>
      val userCounts = u.map(_ ->(new Timestamp(0), 0, 0)).toMap
      db.run((directMessages.filter(_.toUserId === userId)
        join users on { case (directMessage, user) => directMessage.fromUserId === user.id }
        joinLeft directMessageReadStatuses on { case ((directMessage, user), status) => directMessage.id === status.directMessageId })
        .groupBy { case ((directMessage, user), status) => (user.id, user.login, user.name, user.avatar) }
        .map { case ((uId, userLogin, userName, userAvatar), g) =>
          (uId, userLogin, userName, userAvatar, g.map(gg => gg._2.map(_.directMessageId)).length, g.length, g.map(_._1._1.date).max)
        }.result
      ).map { d =>
        val directMessagesCounts = d.map { case (uId, userLogin, userName, userAvatar, readCount, count, date) =>
         User(uId, userLogin, userName, userAvatar) -> (date.get, readCount, count)
        }.toMap
        (userCounts ++ directMessagesCounts).toSeq.sortBy(-_._2._1.getTime).map { case (user, (date, readCount, count)) => (user, readCount, count) }
      }
    }
  }
}
