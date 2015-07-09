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

        val uQ = dao.users.filter(_.email === "test@test")
        uQ.firstOption match {
          case Some(_) =>
          case None =>
            dao.users += new User(login = "test", email = "test@test", name = "Test", avatar = None)
        }
        val u = uQ.first

        val tQ = dao.topics.filter(_.userId === u.id)
        tQ.delete

        val tId = (dao.topics returning dao.topics.map(_.id)) +=
          new Topic(groupId = g.id, userId = u.id, date = new DateTime(), text = "test")

        tQ.list.size must equalTo(1)

        val mQ = dao.messages.filter(_.topicId === tId)
        mQ.delete

        dao.messages += new Message(groupId = g.id, topicId = tId, userId = u.id, date = new DateTime(), text = "test")

        mQ.list.size must equalTo(1)

        mQ.delete
        tQ.delete
        uQ.delete
        gQ.delete
      }
    }
  }
}
