import org.joda.time.DateTime
import org.specs2.mutable._

import play.api.db.slick.DB
import myUtils.MyPostgresDriver.simple._
import play.api.test._
import play.api.test.Helpers._
import models._
import models.current.dao

class DBSpec extends Specification {
  "DB" should {
    "work as expected" in new WithApplication {
      DB.withSession { implicit s: Session =>
        val gQ = dao.groups.filter(_.id === "test")
        gQ.firstOption match {
          case Some(_) =>case Some(_) =>
          case None =>
            dao.groups += new Group("test")
        }
        val g = gQ.first

        val uQ = dao.users.filter(_.login === "test")
        uQ.firstOption match {
          case Some(_) =>
          case None =>
            dao.users += new User(login = "test", name = "Test", avatar = None)
        }
        val u = uQ.first

        val tQ = dao.topics.filter(_.userId === u.id)
        tQ.delete

        val tId = (dao.topics returning dao.topics.map(_.id)) +=
          new Topic(groupId = g.id, userId = u.id, date = new DateTime(), text = "test")

        tQ.list.size must equalTo(1)

        val cQ = dao.comments.filter(_.topicId === tId)
        cQ.delete

        dao.comments += new Comment(groupId = g.id, topicId = tId, userId = u.id, date = new DateTime(), text = "test")

        cQ.list.size must equalTo(1)

        val uQ2 = dao.users.filter(_.login === "test2")
        uQ2.firstOption match {
          case Some(_) =>
          case None =>
            dao.users += new User(login = "test2", name = "Test 2", avatar = None)
        }
        val u2 = uQ2.first

        val dmQ = dao.directMessages.filter(dm => dm.fromUserId === u.id && dm.toUserId === u2.id)
        dmQ.delete

        val dmId = (dao.directMessages returning dao.directMessages.map(_.id)) +=
          new DirectMessage(fromUserId = u.id, toUserId = u2.id, date = new DateTime(), text = "test")

        dmQ.list.size must equalTo(1)


        dmQ.delete
        uQ2.delete
        cQ.delete
        tQ.delete
        uQ.delete
        gQ.delete
      }
    }
  }
}
