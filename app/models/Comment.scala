package models

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.Future

case class Comment(id: Long = 0, groupId: Long, topicId: Long, userId: Long, date: Timestamp, text: String) extends
AbstractGroupMessage

trait CommentsComponent extends HasDatabaseConfigProvider[JdbcProfile] with GroupsComponent with UsersComponent with TopicsComponent {
  protected val driver: JdbcProfile
  import driver.api._

  class CommentsTable(tag: Tag) extends Table[Comment](tag, "comments") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def groupId = column[Long]("group_id")
    def topicId = column[Long]("topic_id")
    def userId = column[Long]("user_id")
    def date = column[Timestamp]("date")
    def text = column[String]("text", O.SqlType("text"))

    def group = foreignKey("comment_group_fk", groupId, groups)(_.id)
    def topic = foreignKey("comment_topic_fk", topicId, topics)(_.id)
    def user = foreignKey("comment_user_fk", userId, users)(_.id)

    def * = (id, groupId, topicId, userId, date, text) <>(Comment.tupled, Comment.unapply)
  }

  val comments = TableQuery[CommentsTable]
}

  @Singleton()
class CommentsDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with CommentsComponent {

    import driver.api._

    def insert(comment: Comment): Future[Long] = {
      db.run((comments returning comments.map(_.id)) += comment)
    }
  }
