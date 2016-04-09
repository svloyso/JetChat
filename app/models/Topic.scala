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

trait TopicsComponent extends HasDatabaseConfigProvider[JdbcProfile]
  with GroupsComponent
  with UsersComponent
{
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

  def topicsByGroupId(
    groupId: Rep[Long],
    topics: Query[TopicsTable, Topic, Seq] = allTopics.to[Seq])
  = topics.filter(_.groupId === groupId)

  def topicsByUserId(
    userId: Long,
    topics: Query[TopicsTable, Topic, Seq] = allTopics.to[Seq])
  = topics.filter(_.userId === userId)

  def topicsByQuery(
    query: Option[String],
    messagesChecker: Function2[Option[String], Rep[Long], Rep[Boolean]],
    topics: Query[TopicsTable, Topic, Seq] = allTopics.to[Seq])
  = {
    query match {
      case Some(words) => topics filter {
        topic => topic.text.indexOf(words) >= 0 || messagesChecker(query, topic.id)
      }
      case None => topics
    }
  }

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
  extends HasDatabaseConfigProvider[JdbcProfile]
    with TopicsComponent
    with UsersComponent
    with GroupsComponent
    with CommentsComponent
{
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
         TopicChat(Topic(topicId, gId, uId, topicDate, topicText), Group(gId, groupName), User(uId, null, userName, userAvatar, None, false), updateDate.getOrElse(topicDate), !topicRead, totalCount - readCount)
       }.sortBy(-_.updateDate.getTime)
     }
   }

    def allWithCounts(userId: Long, groupId: Option[Long], query: String): Future[Seq[TopicChat]] = {
      def predicate(topic: TopicsTable, localUserId: Rep[Long]) = {
         groupId match {
          case Some(id) => topic.groupId === id
          case None => localUserId === userId
        }
      }

      val topics = topicsByQuery(Some(query), commentsByQueryAndTopicId(_, _).exists)
      val comments = commentsByQuery(Some(query))

      db.run(
        (topics
          joinLeft allTopicReadStatuses on { case (topic, status) => topic.id === status.topicId && status.userId === userId }
          join allUsers on { case ((topic, status), user) => topic.userId === user.id }
          join allGroups on { case (((topic, status), user), group) => topic.groupId === group.id }
        )
        .filter { case (((topic, _), _), _) =>
          predicate(topic, topic.userId)
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
            joinLeft allTopicReadStatuses on { case ((comment, topic), topicStatus) => topic.id === topicStatus.topicId && topicStatus.userId === userId }
            joinLeft allCommentReadStatuses on { case (((comment, topic), topicStatus), commentStatus) => comment.id === commentStatus.commentId && commentStatus.userId === userId }
            join allUsers on { case ((((comment, topic), topicStatus), commentStatus), user) => topic.userId === user.id }
            join allGroups on { case (((((comment, topic), topicStatus), commentStatus), user), group) => topic.groupId === group.id }
          ).filter { case (((((comment, topic), _), _), _), _) =>
            predicate(topic, comment.userId)
          }.groupBy { case (((((comment, topic), topicStatus), _), user), group) =>
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
            TopicChat(Topic(topicId, gId, userId, topicDate, topicText), Group(gId, groupName), User(uId, null, userName,  null, None, false), updateDate, !readStatus, totalCount - readCount)
          }
        }
      }
    }

  def messages(userId: Long, topicId: Long, query: Option[String], offset: Long, length: Long): Future[Seq[(AbstractMessage, User, Group, Boolean)]] = {
    val comments = commentsByQuery(query)
    db.run(
      (comments.filter(comment => comment.topicId === topicId)
        join allUsers on { case (comment, user) => comment.userId === user.id }
        join allGroups on { case ((comment, user), group) => comment.groupId === group.id }
        joinLeft allCommentReadStatuses on { case (((comment, user), group), status) => comment.id === status.commentId && status.userId === userId }
      ).map { case (((comment, user), group), status) =>
        (comment, user, group, status.map(_.commentId).isDefined)
      }.sortBy(_._1.date desc).drop(offset).take(length).result
    ).flatMap { case commentTuples =>
        if (commentTuples.length < length) {
          val topics = topicsByQuery(query, commentsByQueryAndTopicId(_, _).exists)
          db.run(
            (topics.filter(_.id === topicId)
              join allUsers on { case (topic, user) => topic.userId === user.id }
              join allGroups on { case ((topic, user), group) => topic.groupId === group.id }
              joinLeft allTopicReadStatuses on { case (((topic, user), group), status) => topic.id === status.topicId && status.userId === userId }
            ).map { case (((topic, user), group), status) =>
              (topic, user, group, status.map(_.topicId).isDefined)
            }.result.head
          ).map { topicTuple =>
            commentTuples.sortBy(_._1.date.getTime).+:(topicTuple)
          }
        } else {
          Future.successful(commentTuples.sortBy(_._1.date.getTime))
        }
    }
  }
}
