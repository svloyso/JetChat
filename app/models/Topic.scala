package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import play.api.libs.json.Json
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Topic(id: Long = 0, groupId: Long, userId: Long, date: Timestamp, text: String) extends AbstractGroupMessage

case class TopicReadStatus(topicId: Long = 0, userId: Long) extends ReadStatus

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
}

@Singleton()
class TopicsDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with TopicsComponent with UsersComponent with GroupsComponent with CommentsComponent {

  import driver.api._

  def findById(id: Long): Future[Option[Topic]] = {
    db.run(topics.filter(_.id === id).result.headOption)
  }

  def insert(topic: Topic): Future[Long] = {
    db.run((topics returning topics.map(_.id)) += topic)
  }

  def allWithCounts(userId: Long, groupId: Option[Long]): Future[Seq[(Long, Timestamp, String, Long, String, Long, String, Int, Int)]] = {
    db.run((topics
      joinLeft topicReadStatuses on { case (topic, topicReadStatus) => topic.id === topicReadStatus.topicId && topicReadStatus.userId === userId }
      join users on { case ((topic, topicReadStatus), user) => topic.userId === user.id }
      join groups on { case (((topic, topicReadStatus), user), group) => topic.groupId === group.id }).filter { case (((topic, topicReadStatus), user), group) =>
      groupId match {
        case Some(id) => topic.groupId === id
        case None => topic.groupId === topic.groupId
      }
    }.sortBy(_._1._1._1.date desc).map {
      case (((topic, topicReadStatus), user), group) =>
        (topic.id, topic.date, topic.text, group.id, group.name, user.id, user.name) -> (topicReadStatus.isDefined, 0)
    }.result).flatMap { case f =>
      val userTopics = f.map { case ((topicId, topicDate, topicText, gId, groupName, uId, userName), (read, c)) =>
        (topicId, topicDate, topicText, gId, groupName, uId, userName) -> (if (read) 1 else 0, c) }.toMap

      db.run((comments
        join topics on { case (comment, topic) => comment.topicId === topic.id }
        joinLeft commentReadStatuses on { case ((comment, topic), commentReadStatus) => comment.id === commentReadStatus.commentId && commentReadStatus.userId === userId }
        join users on { case (((comment, topic), commentReadStatus), user) => topic.userId === user.id }
        join groups on { case ((((comment, topic), commentReadStatus), user), group) => topic.groupId === group.id }).filter { case ((((topic, comment), commentReadStatus), user), group) =>
        groupId match {
          case Some(id) => topic.groupId === id
          case None => topic.groupId === topic.groupId
        }
      }.groupBy { case ((((comment, topic), commentReadStatus), user), group) =>
        (topic.id, topic.date, topic.text, group.id, group.name, user.id, user.name)
      }.map { case ((topicId, topicDate, topicText, gId, groupName, uId, userName), g) =>
        (topicId, topicDate, topicText, gId, groupName, uId, userName, g.map(_._1._1._2.map(_.commentId)).length, g.map(_._1._1._1._1.id).countDistinct, g.map(_._1._1._1._1.date).max)
      }.sortBy(_._10 desc).result).map { case f =>
        val commentedTopics = f.map { case (topicId, topicDate, topicText, gId, groupName, uId, userName, readCount, c, d) =>
          (topicId, topicDate, topicText, gId, groupName, uId, userName) -> (readCount, c)
        }.toMap

        val total = (userTopics ++ commentedTopics).toSeq.sortBy(-_._1._2.getTime)

        total.map { case ((topicId, topicDate, topicText, gId, groupName, uId, userName), (readCount, c)) =>
          (topicId, topicDate, topicText, gId, groupName, uId, userName, readCount, c)
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
