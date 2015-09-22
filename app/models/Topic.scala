package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import play.api.libs.json.Json
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Topic(id: Long = 0, groupId: Long, userId: Long, date: Timestamp, text: String) extends AbstractGroupMessage

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

  def allWithCounts(userId: Long, groupId: Option[Long]): Future[Seq[(Long, Timestamp, String, Long, String, Long, String, Int)]] = {
    db.run((topics join users on { case (topic, user) => topic.userId === user.id }
      join groups on { case ((topic, user), group) => topic.groupId === group.id }).filter { case ((topic, user), group) =>
      groupId match {
        case Some(id) => topic.groupId === id
        case None => topic.groupId === topic.groupId
      }
    }.sortBy(_._1._1.date desc).map {
      case ((topic, user), group) =>
        (topic.id, topic.date, topic.text, group.id, group.name, user.id, user.name) -> 0
    }.result).flatMap { case f =>
      val userTopics = f.toMap

      db.run((topics joinLeft comments on { case (topic, comment) => comment.topicId === topic.id }
        join users on { case ((topic, comment), user) => topic.userId === user.id }
        join groups on { case (((topic, comment), user), group) => topic.groupId === group.id }).filter { case (((topic, comment), user), group) =>
        groupId match {
          case Some(id) => topic.groupId === id
          case None => topic.groupId === topic.groupId
        }
      }.groupBy { case (((topic, comment), user), group) =>
        (topic.id, topic.date, topic.text, group.id, group.name, user.id, user.name)
      }.map { case ((topicId, topicDate, topicText, gId, groupName, uId, userName), g) =>
        (topicId, topicDate, topicText, gId, groupName, uId, userName, g.map(_._1._1._1.id).countDistinct, g.map(_._1._1._1.date).max)
      }.sortBy(_._9 desc).result).map { case f =>
        val commentedTopics = f.map { case (topicId, topicDate, topicText, gId, groupName, uId, userName, c, d) =>
          (topicId, topicDate, topicText, gId, groupName, uId, userName) -> c
        }.toMap

        val total = (userTopics ++ commentedTopics).toSeq.sortBy(-_._1._2.getTime)

        total.map { case ((topicId, topicDate, topicText, gId, groupName, uId, userName), c) =>
          (topicId, topicDate, topicText, gId, groupName, uId, userName, c)
        }
      }
    }
  }

  def messages(userId: Long, topicId: Long): Future[Seq[(AbstractMessage, User, Group)]] = {
    db.run((topics.filter(_.id === topicId)
      join users on { case (topic, user) => topic.userId === user.id }
      join groups on { case ((topic, user), group) => topic.groupId === group.id })
      .map { case ((topic, user), group) => (topic, user, group) }.result.head
    ).flatMap { case t =>
      db.run((comments.filter(comment => comment.topicId === topicId)
        join users on { case (comment, user) => comment.userId === user.id }
        join groups on { case ((c, user), group) => c.groupId === group.id })
        .map { case ((comment, user), group) =>
          (comment, user, group)
        }.sortBy(_._1.date).result
      ).map { case f =>
        f.+:(t)
      }
    }
  }
}
