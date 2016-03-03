package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import play.api.libs.json.Json
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Topic(id: Long = 0, groupId: Long, userId: Long, date: Timestamp, text: String) extends AbstractGroupMessage

case class TopicReadStatus(topicId: Long, userId: Long) extends ReadStatus

case class TopicFollowStatus(topicId: Long, userId: Long)

trait AbstractChat {
  def user: User
  def text: String
  def updateDate: Timestamp
  def unreadCount: Int
}

case class TopicChat(topic: Topic, group: Group, user: User, updateDate: Timestamp, unread: Boolean, unreadCount: Int) extends AbstractChat {
  override def text: String = topic.text
}

trait TopicsComponent extends HasDatabaseConfigProvider[JdbcProfile] with GroupsComponent with UsersComponent {
  protected val driver: JdbcProfile

  import driver.api._

  class TopicsTable(tag: Tag) extends Table[Topic](tag, "topics") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def groupId = column[Long]("group_id")

    def userId = column[Long]("user_id")

    def date = column[Timestamp]("date")

    def text = column[String]("text", O.SqlType("text"))

    def group = foreignKey("topic_group_fk", groupId, groups)(_.id)

    def user = foreignKey("topic_user_fk", userId, users)(_.id)

    def groupIndex = index("topic_group_index", groupId, unique = false)

    def userGroupIndex = index("topic_user_group_index", (groupId, userId), unique = false)

    def * = (id, groupId, userId, date, text) <>(Topic.tupled, Topic.unapply)
  }

  val topics = TableQuery[TopicsTable]

  class TopicReadStatusesTable(tag: Tag) extends Table[TopicReadStatus](tag, "topic_read_statuses") {
    def topicId = column[Long]("topic_id")

    def userId = column[Long]("user_id")

    def pk = primaryKey("topic_read_status_pk", (topicId, userId))

    def topic = foreignKey("topic_read_status_topic_fk", topicId, topics)(_.id)

    def user = foreignKey("topic_read_status_user_fk", userId, users)(_.id)

    def * = (topicId, userId) <>(TopicReadStatus.tupled, TopicReadStatus.unapply)
  }

  val topicReadStatuses = TableQuery[TopicReadStatusesTable]


  class TopicFollowStatusesTable(tag: Tag) extends Table[TopicFollowStatus](tag, "topic_follow_statuses") {
    def topicId = column[Long]("topic_id")

    def userId = column[Long]("user_id")

    def pk = primaryKey("topic_follow_status_pk", (topicId, userId))

    def topic = foreignKey("topic_follow_status_topic_fk", topicId, topics)(_.id)

    def user = foreignKey("topic_follow_status_user_fk", userId, users)(_.id)

    def * = (topicId, userId) <>(TopicFollowStatus.tupled, TopicFollowStatus.unapply)
  }

  val topicFollowStatuses = TableQuery[TopicFollowStatusesTable]
}

@Singleton()
class TopicsDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with TopicsComponent with UsersComponent with GroupsComponent with CommentsComponent {

  import driver.api._

  def findById(id: Long): Future[Option[Topic]] = {
    db.run(topics.filter(_.id === id).result.headOption)
  }

  def insert(topic: Topic): Future[Long] = {
    db.run((topics returning topics.map(_.id)) += topic).flatMap( id =>
      db.run(topicReadStatuses += TopicReadStatus(id, topic.userId)).map(_ => id)
    )
  }

  def markAsRead(userId: Long, topicIds: Seq[Long]): Future[Option[Int]] = {
    db.run(topicReadStatuses ++= topicIds.map(TopicReadStatus(_, userId)))
  }

  /**
    * @CriticalPerformanceProblems
    * Query 1
    * Execution: 9 seconds:
    * SQL: SELECT
    *   x2.`name`,
    *   x3.`text`,
    *   count(1),
    *   x2.`id`,
    *   count(x4.`direct_message_id`),
    *   x2.`login`,
    *   x2.`avatar`,
    *   max(x5.x6)
    * FROM (SELECT
    *         `to_user_id`   AS x7,
    *         `date`         AS x6,
    *         `text`         AS x8,
    *         `id`           AS x9,
    *         `from_user_id` AS x10
    *       FROM `direct_messages`
    *       WHERE `to_user_id` = 10) x5 INNER JOIN `direct_messages` x3 ON x3.`date` = (SELECT max(`date`)
    *                                                                                   FROM `direct_messages`
    *                                                                                   WHERE ((`to_user_id` = 10) AND
    *                                                                                          (`from_user_id` = x5.x10)) OR
    *                                                                                         ((`from_user_id` = 10) AND
    *                                                                                          (`to_user_id` = x5.x7)))
    *   INNER JOIN `users` x2 ON x5.x10 = x2.`id`
    *   LEFT OUTER JOIN `direct_message_read_statuses` x4 ON x5.x9 = x4.`direct_message_id`
    * GROUP BY x2.`id`, x2.`login`, x2.`name`, x2.`avatar`, x3.`text`
    */
  def allWithCounts(userId: Long, groupId: Option[Long]): Future[Seq[TopicChat]] = {
    db.run(
      (topics
        joinLeft topicReadStatuses on { case (topic, status) => topic.id === status.topicId && status.userId === userId }
        join users on { case ((topic, status), user) => topic.userId === user.id }
        join groups on { case (((topic, status), user), group) => topic.groupId === group.id }
      ).filter { case (((topic, status), user), group) =>
        groupId match {
          case Some(id) => topic.groupId === id
          case None => topic.userId === userId
        }
      }.sortBy(_._1._1._1.date desc).map {
        case (((topic, status), user), group) =>
          (topic.id, topic.date, topic.text, group.id, group.name, user.id, user.name) -> status.map(_.topicId).isDefined
      }.result
    ).flatMap { case f =>
      val userTopics = f.map { case ((topicId, topicDate, topicText, gId, groupName, uId, userName), (readStatus)) =>
        (topicId, topicDate, topicText, gId, groupName, uId, userName) ->(topicDate, readStatus, 0, 0)
      }.toMap

      db.run(
        (comments
          join topics on { case (comment, topic) => comment.topicId === topic.id }
          joinLeft topicReadStatuses on { case ((comment, topic), topicStatus) => topic.id === topicStatus.topicId && topicStatus.userId === userId }
          joinLeft commentReadStatuses on { case (((comment, topic), topicStatus), commentStatus) => comment.id === commentStatus.commentId && commentStatus.userId === userId }
          join users on { case ((((comment, topic), topicStatus), commentStatus), user) => topic.userId === user.id }
          join groups on { case (((((comment, topic), topicStatus), commentStatus), user), group) => topic.groupId === group.id }
        ).filter { case (((((comment, topic), topicStatus), commentStatus), user), group) =>
          groupId match {
            case Some(id) => topic.groupId === id
            case None => comment.userId === userId
          }
        }.groupBy { case (((((comment, topic), topicStatus), commentStatus), user), group) =>
          (topic.id, topic.date, topic.text, group.id, group.name, user.id, user.name, topicStatus)
        }.map { case ((topicId, topicDate, topicText, gId, groupName, uId, userName, topicStatus), g) =>
          (topicId, topicDate, topicText, gId, groupName, uId, userName, topicStatus.map(_.topicId).isDefined, g.map(_._1._1._2.map(_.commentId)).countDefined, g.map(_._1._1._1._1._1.id).countDistinct, g.map(_._1._1._1._1._1.date).max)
        }.sortBy(_._10 desc).result
      ).map { case f =>
        val commentedTopics = f.map { case (topicId, topicDate, topicText, gId, groupName, uId, userName, topicReadStatus, readCount, c, d) =>
          (topicId, topicDate, topicText, gId, groupName, uId, userName) ->(d.get, topicReadStatus, readCount, c)
        }.toMap

        val total = (userTopics ++ commentedTopics).toSeq.sortBy(-_._2._1.getTime)

        total.map { case ((topicId, topicDate, topicText, gId, groupName, uId, userName), (updateDate, readStatus, readCount, totalCount)) =>
          TopicChat(Topic(topicId, gId, userId, topicDate, topicText), Group(gId, groupName), User(uId, null, userName,  null), updateDate, !readStatus, totalCount - readCount)
        }
      }
    }
  }

  def messages(userId: Long, topicId: Long): Future[Seq[(AbstractMessage, User, Group, Boolean)]] = {
    db.run(
      (topics.filter(_.id === topicId)
      join users on { case (topic, user) => topic.userId === user.id }
      join groups on { case ((topic, user), group) => topic.groupId === group.id }
      joinLeft topicReadStatuses on { case (((topic, user), group), status) => topic.id === status.topicId && status.userId === userId })
      .map { case (((topic, user), group), status) =>
        (topic, user, group, status.map(_.topicId).isDefined)
      }.result.head
    ).flatMap { case t =>
      db.run((comments.filter(comment => comment.topicId === topicId)
        join users on { case (comment, user) => comment.userId === user.id }
        join groups on { case ((comment, user), group) => comment.groupId === group.id }
        joinLeft commentReadStatuses on { case (((comment, user), group), status) => comment.id === status.commentId && status.userId === userId }
        )
        .map { case (((comment, user), group), status) =>
          (comment, user, group, status.map(_.commentId).isDefined)
        }.sortBy(_._1.date).result
      ).map { case f =>
        f.+:(t)
      }
    }
  }
}
