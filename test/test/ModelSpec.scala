package test

import models.{User, UsersDAO}
import org.specs2.mutable.Specification
import play.api.{Configuration, GlobalSettings, Application}
import play.api.test.{FakeApplication, WithApplication}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import play.api.test.Helpers._


class ModelSpec extends Specification {
  val appWithMemoryDatabase = FakeApplication()

  def usersDao(implicit app: Application) = {
    val app2UsersDAO = Application.instanceCache[UsersDAO]
    app2UsersDAO(app)
  }

  "User model" should {
    "work as expected" in new WithApplication(appWithMemoryDatabase) {
        var test = Await.result(usersDao.findByLogin("test"), Duration.Inf)
        if (test.isEmpty) {
          Await.result(usersDao.insert(User(0, "test", "Test", None)), Duration.Inf)
          test = Await.result(usersDao.findByLogin("test"), Duration.Inf)
        }

        val users = Await.result(usersDao.all, Duration.Inf)
        users.length mustEqual 1

//      DB.withSession { implicit s: Session =>
//        val gQ = dao.groups.filter(_.name === "test")
//        gQ.firstOption match {
//          case Some(_) =>case Some(_) =>
//          case None =>
//            dao.groups += new Group(name = "test")
//        }
//        val g = gQ.first
//
//        val uQ = dao.users.filter(_.login === "test")
//        uQ.firstOption match {
//          case Some(_) =>
//          case None =>
//            dao.users += new User(login = "test", name = "Test", avatar = None)
//        }
//        val u = uQ.first
//
//        val tQ = dao.topics.filter(_.userId === u.id)
//        tQ.delete
//
//        val tId = (dao.topics returning dao.topics.map(_.id)) +=
//          new Topic(groupId = g.id, userId = u.id, date = new DateTime(), text = "test")
//
//        tQ.list.size must equalTo(1)
//
//        val cQ = dao.comments.filter(_.topicId === tId)
//        cQ.delete
//
//        dao.comments += new Comment(groupId = g.id, topicId = tId, userId = u.id, date = new DateTime(), text = "test")
//
//        cQ.list.size must equalTo(1)
//
//        val uQ2 = dao.users.filter(_.login === "test2")
//        uQ2.firstOption match {
//          case Some(_) =>
//          case None =>
//            dao.users += new User(login = "test2", name = "Test 2", avatar = None)
//        }
//        val u2 = uQ2.first
//
//        val dmQ = dao.directMessages.filter(dm => dm.fromUserId === u.id && dm.toUserId === u2.id)
//        dmQ.delete
//
//        val dmId = (dao.directMessages returning dao.directMessages.map(_.id)) +=
//          new DirectMessage(fromUserId = u.id, toUserId = u2.id, date = new DateTime(), text = "test")
//
//        dmQ.list.size must equalTo(1)
//
//
//        dmQ.delete
//        uQ2.delete
//        cQ.delete
//        tQ.delete
//        uQ.delete
//        gQ.delete
//      }
    }
  }
}
