package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}
import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import slick.driver.JdbcProfile
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Group(id: Long = 0, name: String)

case class GroupFollowStatus(groupId: Long, userId: Long)

trait GroupsComponent extends HasDatabaseConfigProvider[JdbcProfile] with UsersComponent {
  protected val driver: JdbcProfile

  import driver.api._

  class GroupsTable(tag: Tag) extends Table[Group](tag, "groups") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def nameIndex = index("group_name_index", name, unique = true)
    def * = (id, name) <> (Group.tupled, Group.unapply)
  }

  val allGroups = TableQuery[GroupsTable]

  def groupsByQuery(
    query: Option[String],
    messagesChecker: Function2[Option[String], Rep[Long], Rep[Boolean]],
    groups: Query[GroupsTable, Group, Seq] = allGroups.to[Seq])
  = {
    query match {
      case Some(words) => groups filter {
        group => group.name.indexOf(words) >=0 || messagesChecker(query, group.id)
      }
      case None => groups
    }
  }

  class GroupFollowStatusesTable(tag: Tag) extends Table[GroupFollowStatus](tag, "topic_follow_statuses") {
    def groupId = column[Long]("group_id")
    def userId = column[Long]("user_id")
    def pk = primaryKey("group_follow_status_pk", (groupId, userId))
    def topic = foreignKey("group_follow_status_group_fk", groupId, allGroups)(_.id)
    def user = foreignKey("group_follow_status_user_fk", userId, allUsers)(_.id)
    def * = (groupId, userId) <> (GroupFollowStatus.tupled, GroupFollowStatus.unapply)
  }

  val allGroupFollowStatuses = TableQuery[GroupFollowStatusesTable]
}

@Singleton()
class GroupsDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile]
    with GroupsComponent
    with TopicsComponent
    with CommentsComponent
{
  import driver.api._

  def insert(group: Group): Future[Long] = {
    db.run((allGroups returning allGroups.map(_.id)) += group)
  }

  def allWithCounts(userId: Long, query: Option[String]): Future[Seq[(Group, Int, Int)]] = {
    val groups = groupsByQuery(query, commentsByQueryAndGroupId(_, _).exists)
    val topics = topicsByQuery(query, commentsByQueryAndTopicId(_, _).exists, topicsByUserId(userId))
    val comments = commentsByQuery(query)

    db.run(groups.result).flatMap { f =>
      val groupMap = f.map { g => g.id ->(g.name, new Timestamp(0), 0, 0) }.toMap

      db.run(
        (topics
          joinLeft topics on { case (topic, myTopic) => topic.id === myTopic.id }
          joinLeft allTopicReadStatuses on { case ((topic, myTopic), topicReadStatus) => topic.id === topicReadStatus.topicId && topicReadStatus.userId === userId }
          join groups on { case (((topic, myTopic), topicReadStatus), group) => topic.groupId === group.id }
        ).groupBy { case (((topic, myTopic), topicReadStatus), group) =>
          (group.id, group.name)
        }.map { case ((groupId, groupName), g) =>
          (groupId, groupName, g.map(_._1._1._2.map(_.date)).max, g.map(gg => gg._1._2.map(_.topicId)).countDefined, g.length)
        }.result
      ).flatMap { f =>
        val topicMap = f.map { case (groupId, groupName, myDate, unreadCount, count) =>
          groupId ->(groupName, myDate.getOrElse(new Timestamp(0)), unreadCount, count)
        }.toMap

        db.run(
          (comments
            joinLeft comments on { case (comment, myComment) => comment.id === myComment.id && myComment.userId === userId }
            joinLeft allCommentReadStatuses on { case ((comment, myComment), commentReadStatus) => comment.id === commentReadStatus.commentId && commentReadStatus.userId === userId }
            join allGroups on { case (((comment, _), _), group) => comment.groupId === group.id }
          ).groupBy { case (((_, _), _), group) => (group.id, group.name) }
          .map { case ((groupId, groupName), g) =>
            (groupId, groupName, g.map(_._1._1._2.map(_.date)).max, g.map(gg => gg._1._2.map(_.commentId)).countDefined, g.length)
          }.result
        ).map { f =>
          val commentMap = f.map { case (groupId, groupName, myDate, unreadCount, count) => groupId ->(groupName, myDate.getOrElse(new Timestamp(0)), unreadCount, count) }.toMap

          val groupTotal = groupMap ++ topicMap.map { case (groupId, (groupName, topicDate, topicReadCount, topicCount)) =>
            val (_, commentDate, commentReadCount, commentCount) = commentMap.getOrElse(groupId, (groupName, new Timestamp(0), 0, 0))
            groupId -> (groupName,
              new Timestamp(Math.max(topicDate.getTime(), commentDate.getTime)),
              topicReadCount + commentReadCount,
              topicCount + commentCount)
          }

          groupTotal.toSeq.sortBy(g => - g._2._2.getTime).map { case (groupId, (groupName, myDate, readCount, count)) =>
            (Group(groupId, groupName), readCount, count)
          }
        }
      }
    }
  }
}