package models

import javax.inject.{Inject, Singleton}
import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import slick.driver.JdbcProfile
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Group(id: Long = 0, name: String)

trait GroupsComponent extends HasDatabaseConfigProvider[JdbcProfile] {
  protected val driver: JdbcProfile

  import driver.api._

  class GroupsTable(tag: Tag) extends Table[Group](tag, "groups") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def name = column[String]("name")

    def nameIndex = index("group_name_index", name, unique = true)

    def * = (id, name) <>(Group.tupled, Group.unapply)
  }

  val groups = TableQuery[GroupsTable]
}

@Singleton()
class GroupsDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with GroupsComponent with TopicsComponent with CommentsComponent {

  import driver.api._

  def insert(group: Group): Future[Long] = {
    db.run((groups returning groups.map(_.id)) += group)
  }

  def allWithCounts(userId: Long): Future[Seq[(Group, Int, Int)]] = {
    db.run(groups.result).flatMap { f =>
      val groupMap = f.map { g => g.id ->(g.name, 0, 0) }.toMap

      db.run((topics
        joinLeft topicReadStatuses on { case (topic, topicReadStatus) => topic.id === topicReadStatus.topicId && topicReadStatus.userId === userId }
        join groups on { case ((topic, topicReadStatus), group) => topic.groupId === group.id })
        .groupBy { case ((topic, topicReadStatus), group) => (group.id, group.name) }
        .map { case ((groupId, groupName), g) => (groupId, groupName,
          g.map(gg => gg._1._2.map(_.topicId)).length,
          g.length) }.result)
        .flatMap { f =>
          val topicMap = f.map { case (groupId, groupName, unreadCount, count) =>
            groupId ->(groupName, unreadCount, count)
          }.toMap

          db.run((comments
            joinLeft commentReadStatuses on { case (comment, commentReadStatus) => comment.id === commentReadStatus.commentId && commentReadStatus.userId === userId }
            join groups on { case ((comment, commentReadStatus), group) => comment.groupId === group.id })
            .groupBy { case (comment, group) =>
              (group.id, group.name)
            }
            .map { case ((groupId, groupName), g) => (groupId, groupName,
              g.map(gg => gg._1._2.map(_.commentId)).length,
              g.length) }.result)
            .map { f =>
              val commentMap = f.map { case (groupId, groupName, unreadCount, count) =>
                groupId ->(groupName, unreadCount, count)
              }.toMap

              val groupTotal = groupMap ++ topicMap.map { case (groupId, (groupName, topicReadCount, topicCount)) =>
                val (_, commentReadCount, commentCount) = commentMap.getOrElse(groupId, (groupName, 0, 0))
                groupId -> (groupName, topicReadCount + commentReadCount, topicCount + commentCount)
              }

              groupTotal.toSeq.sortBy(g => g._2._3).map { case (groupId, (groupName, readCount, count)) =>
                (Group(groupId, groupName), readCount, count)
              }
            }
        }
    }
  }
}