package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Topic(id: Long = 0, groupId: Long, userId: Long, date: Timestamp, text: String) extends AbstractGroupMessage

case class TopicReadStatus(topicId: Long, userId: Long)

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
    def group = foreignKey("topic_group_fk", groupId, allGroups)(_.id)
    def user = foreignKey("topic_user_fk", userId, allUsers)(_.id)
    def groupIndex = index("topic_group_index", groupId, unique = false)
    def userGroupIndex = index("topic_user_group_index", (groupId, userId), unique = false)
    def * = (id, groupId, userId, date, text) <> (Topic.tupled, Topic.unapply)
  }

  val allTopics = TableQuery[TopicsTable]

  class TopicReadStatusesTable(tag: Tag) extends Table[TopicReadStatus](tag, "topic_read_statuses") {
    def topicId = column[Long]("topic_id")
    def userId = column[Long]("user_id")
    def pk = primaryKey("topic_read_status_pk", (topicId, userId))
    def topic = foreignKey("topic_read_status_topic_fk", topicId, allTopics)(_.id)
    def user = foreignKey("topic_read_status_user_fk", userId, allUsers)(_.id)
    def * = (topicId, userId) <> (TopicReadStatus.tupled, TopicReadStatus.unapply)
  }

  val allTopicReadStatuses = TableQuery[TopicReadStatusesTable]

  class TopicFollowStatusesTable(tag: Tag) extends Table[TopicFollowStatus](tag, "topic_follow_statuses") {
    def topicId = column[Long]("topic_id")
    def userId = column[Long]("user_id")
    def pk = primaryKey("topic_follow_status_pk", (topicId, userId))
    def topic = foreignKey("topic_follow_status_topic_fk", topicId, allTopics)(_.id)
    def user = foreignKey("topic_follow_status_user_fk", userId, allUsers)(_.id)
    def * = (topicId, userId) <> (TopicFollowStatus.tupled, TopicFollowStatus.unapply)
  }

  val allTopicFollowStatuses = TableQuery[TopicFollowStatusesTable]
}

@Singleton()
class TopicsDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with TopicsComponent with UsersComponent with GroupsComponent with CommentsComponent {

  import driver.api._

  def findById(id: Long): Future[Option[Topic]] = {
    db.run(allTopics.filter(_.id === id).result.headOption)
  }

  def insert(topic: Topic): Future[Long] = {
    db.run((allTopics returning allTopics.map(_.id)) += topic).flatMap(id =>
      db.run(allTopicReadStatuses += TopicReadStatus(id, topic.userId)).map(_ => id)
    )
  }

  def markAsRead(userId: Long, topicIds: Seq[Long]): Future[Option[Int]] = {
    db.run(allTopicReadStatuses ++= topicIds.map(TopicReadStatus(_, userId)))
  }

  def allWithCounts(userId: Long, groupId: Option[Long]): Future[Seq[TopicChat]] = {
    val sql = if (groupId.isDefined)
      sql"""SELECT t.`id`, t.`text`, t.`date`, u.`id`, u.`name`, u.`avatar`, g.`id`, g.`name`, MAX(c.`date`), ts.`topic_id` IS NOT NULL, count(cs.`comment_id`), count(c.`id`)
            FROM `topics` t
              LEFT OUTER JOIN `comments` c ON c.topic_id = t.id
              LEFT JOIN `users` u ON t.`user_id` = u.id
              LEFT JOIN `groups` g ON t.`group_id` = g.id
              LEFT JOIN `comment_read_statuses` cs ON c.`id` = cs.`comment_id` AND cs.`user_id` = $userId
              LEFT JOIN `topic_read_statuses` ts ON t.`id` = ts.`topic_id` AND ts.`user_id` = $userId
            WHERE t.`group_id` = ${groupId.get}
            GROUP BY t.`id`, t.`text`, t.`date`, u.`id`"""
    else
      sql"""SELECT t.`id`, t.`text`, t.`date`, u.`id`, u.`name`, u.`avatar`, g.`id`, g.`name`, MAX(c.`date`), ts.`topic_id` IS NOT NULL, count(cs.`comment_id`), count(c.`id`)
            FROM `topics` t
              LEFT OUTER JOIN `comments` c ON c.topic_id = t.id
              LEFT JOIN `users` u ON t.`user_id` = u.id
              LEFT JOIN `groups` g ON t.`group_id` = g.id
              LEFT JOIN `comment_read_statuses` cs ON c.`id` = cs.`comment_id` AND cs.`user_id` = $userId
              LEFT JOIN `topic_read_statuses` ts ON t.`id` = ts.`topic_id` AND ts.`user_id` = $userId
            WHERE t.`user_id` = $userId OR t.`id` IN (SELECT DISTINCT `topic_id` FROM `comments` WHERE user_id = $userId)
            GROUP BY t.`id`, t.`text`, t.`date`, u.`id`"""
    db.run(
      sql.as[(Long, String, Timestamp, Long, String, Option[String], Long, String, Option[Timestamp], Boolean, Int, Int)]
    ).map { case results =>
      results.map { case (topicId, topicText, topicDate, uId, userName, userAvatar, gId, groupName, updateDate, topicRead, readCount, totalCount) =>
        TopicChat(Topic(topicId, gId, uId, topicDate, topicText), Group(gId, groupName), User(uId, null, userName, userAvatar), updateDate.getOrElse(topicDate), !topicRead, totalCount - readCount)
      }.sortBy(-_.updateDate.getTime)
    }
  }

  def messages(userId: Long, topicId: Long): Future[Seq[(AbstractMessage, User, Group, Boolean)]] = {
    db.run(
      (allTopics.filter(_.id === topicId)
      join allUsers on { case (topic, user) => topic.userId === user.id }
      join allGroups on { case ((topic, user), group) => topic.groupId === group.id }
      joinLeft allTopicReadStatuses on { case (((topic, user), group), status) => topic.id === status.topicId && status.userId === userId })
      .map { case (((topic, user), group), status) =>
        (topic, user, group, status.map(_.topicId).isDefined)
      }.result.head
    ).flatMap { case t =>
      db.run((allComments.filter(comment => comment.topicId === topicId)
        join allUsers on { case (comment, user) => comment.userId === user.id }
        join allGroups on { case ((comment, user), group) => comment.groupId === group.id }
        joinLeft allCommentReadStatuses on { case (((comment, user), group), status) => comment.id === status.commentId && status.userId === userId }
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
