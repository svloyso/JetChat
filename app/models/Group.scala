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

  def allWithCounts(userId: Long): Future[Seq[(Group, Int)]] = {
    db.run(groups.result).flatMap { f =>
      val groupMap = f.map { g => g.id ->(g.name, 0) }.toMap

      db.run((topics.filter(_.userId === userId)
        join groups on { case (topic, group) => topic.groupId === group.id })
        .groupBy { case (topic, group) => (group.id, group.name) }
        .map { case ((groupId, groupName), g) => (groupId, groupName, g.map(_._1.groupId).countDistinct) }.result)
        .flatMap { f =>
          val topicMap = f.map { case (groupId, groupName, count) =>
            groupId ->(groupName, count)
          }.toMap

          db.run((comments.filter(_.userId === userId) join groups on { case (comment, group) => comment.groupId === group.id })
            .groupBy { case (comment, group) => (group.id, group.name) }
            .map { case ((groupId, groupName), g) => (groupId, groupName, g.map(_._1.groupId).countDistinct) }.result)
            .map { f =>
              val commentMap = f.map { case (groupId, groupName, count) =>
                groupId ->(groupName, count)
              }.toMap

              val groupTotal = groupMap ++ (groupMap ++ topicMap.map { case (groupId, groupTopicToken) =>
                val groupCommentMap = commentMap.getOrElse(groupId, (groupTopicToken._1, 0))
                groupId -> (groupTopicToken._1 -> (groupTopicToken._2 + groupCommentMap._2))
              })

              groupTotal.toSeq.sortBy(a => a._2._2).map { case (groupId, token) =>
                (Group(groupId, token._1), token._2)
              }
            }
        }
    }
  }
}