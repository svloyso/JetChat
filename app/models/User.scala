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

  /**
    * @CriticalPerformanceProblems
    * Query 1
    * Execution: 6 seconds
    * SQL: SELECT
    *   count(x2.`direct_message_id`),
    *   max(x3.x4),
    *   x5.`login`,
    *   x5.`id`,
    *   x6.`text`,
    *   count(1),
    *   x5.`name`,
    *   x5.`avatar`
    * FROM (SELECT
    *         `to_user_id`   AS x7,
    *         `date`         AS x4,
    *         `text`         AS x8,
    *         `id`           AS x9,
    *         `from_user_id` AS x10
    *       FROM `direct_messages`
    *       WHERE `to_user_id` = 9) x3 INNER JOIN `direct_messages` x6 ON x6.`date` = (SELECT max(`date`)
    *                                                                                  FROM `direct_messages`
    *                                                                                  WHERE ((`to_user_id` = 9) AND
    *                                                                                         (`from_user_id` = x3.x10)) OR
    *                                                                                        ((`from_user_id` = 9) AND
    *                                                                                         (`to_user_id` = x3.x7)))
    *   INNER JOIN `users` x5 ON x3.x10 = x5.`id`
    *   LEFT OUTER JOIN `direct_message_read_statuses` x2 ON x3.x9 = x2.`direct_message_id`
    * GROUP BY x5.`id`, x5.`login`, x5.`name`, x5.`avatar`, x6.`text`;
    *
    * Query 2
    * Execution: 4 seconds
    * SQL: SELECT
    *   0,
    *   x2.`name`,
    *   max(x3.x4),
    *   x2.`id`,
    *   x2.`avatar`,
    *   x5.`text`,
    *   x2.`login`
    * FROM (SELECT
    *         `from_user_id` AS x6,
    *         `date`         AS x4,
    *         `to_user_id`   AS x7,
    *         `text`         AS x8,
    *         `id`           AS x9
    *       FROM `direct_messages`
    *       WHERE `from_user_id` = 9) x3, `direct_messages` x5, `users` x2
    * WHERE (x5.`date` = (SELECT max(`date`)
    *                     FROM `direct_messages`
    *                     WHERE ((`to_user_id` = 9) AND (`from_user_id` = x3.x6)) OR
    *                           ((`from_user_id` = 9) AND (`to_user_id` = x3.x7)))) AND (x3.x7 = x2.`id`)
    * GROUP BY x2.`id`, x2.`login`, x2.`name`, x2.`avatar`, x5.`text`;
    */
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
          (uId, userLogin, userName, userAvatar, text, g.map(gg => gg._2.map(_.directMessageId)).countDefined, g.length, g.map(_._1._1._1.date).max)
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
