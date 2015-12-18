package test

import java.sql.Timestamp
import java.util.Calendar

import models._
import models.api.{IntegrationToken, IntegrationTokensDAO}
import org.specs2.mutable.Specification
import play.api.Application
import play.api.test.{FakeApplication, WithApplication}

import scala.concurrent.Await
import scala.concurrent.duration.Duration


class ModelSpec extends Specification {
  def appWithMemoryDatabase() = FakeApplication(additionalConfiguration = Map(
    "slick.dbs.default.driver" -> "slick.driver.H2Driver$",
    "slick.dbs.default.db.driver" -> "org.h2.Driver",
    "slick.dbs.default.db.url" -> "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
    "slick.dbs.default.db.root" -> "sa"
  ))

  def usersDAO(implicit app: Application) = {
    val app2UsersDAO = Application.instanceCache[UsersDAO]
    app2UsersDAO(app)
  }

  def integrationTokensDAO(implicit app: Application) = {
    val app2IntegrationTokensDAO = Application.instanceCache[IntegrationTokensDAO]
    app2IntegrationTokensDAO(app)
  }

  def integrationUsersDAO(implicit app: Application) = {
    val app2IntegrationUsersDAO = Application.instanceCache[IntegrationUsersDAO]
    app2IntegrationUsersDAO(app)
  }

  def integrationGroupsDAO(implicit app: Application) = {
    val app2IntegrationGroupsDAO = Application.instanceCache[IntegrationGroupsDAO]
    app2IntegrationGroupsDAO(app)
  }

  def integrationTopicsDAO(implicit app: Application) = {
    val app2IntegrationTopicsDAO = Application.instanceCache[IntegrationTopicsDAO]
    app2IntegrationTopicsDAO(app)
  }

  def integrationUpdatesDAO(implicit app: Application) = {
    val app2IntegrationUpdatesDAO = Application.instanceCache[IntegrationUpdatesDAO]
    app2IntegrationUpdatesDAO(app)
  }

  "Integration model" should {
    "work as expected" in new WithApplication(appWithMemoryDatabase()) {
      Await.result(usersDAO.insert(User(0, "test-user", "Test User", None)), Duration.Inf)
      val user = Await.result(usersDAO.findByLogin("test-user"), Duration.Inf)

      var token = Await.result(integrationTokensDAO.find(user.get.id, "test-integration"), Duration.Inf)
      token.isDefined mustEqual false

      var m = Await.result(integrationTokensDAO.merge(IntegrationToken(user.get.id, "test-integration", "test-token")), Duration.Inf)
      m mustEqual true

      token = Await.result(integrationTokensDAO.find(user.get.id, "test-integration"), Duration.Inf)
      token.get.token mustEqual "test-token"

      m = Await.result(integrationTokensDAO.merge(IntegrationToken(user.get.id, "test-integration", "test-token-update")), Duration.Inf)
      m mustEqual false

      token = Await.result(integrationTokensDAO.find(user.get.id, "test-integration"), Duration.Inf)
      token.get.token mustEqual "test-token-update"

      Await.result(integrationUsersDAO.merge(IntegrationUser("test-integration", None, "test-integration-user", "Test Integration User", None)), Duration.Inf) mustEqual true
      var integrationUser = Await.result(integrationUsersDAO.findByIntegrationUserId("test-integration-user", "test-integration"), Duration.Inf)
      integrationUser.isDefined mustEqual true

      Await.result(integrationUsersDAO.merge(IntegrationUser("test-integration", Some(user.get.id), "test-integration-user", "Test Integration User", None)), Duration.Inf) mustEqual false
      integrationUser = Await.result(integrationUsersDAO.findByIntegrationUserId("test-integration-user", "test-integration"), Duration.Inf)
      integrationUser.get.userId.get mustEqual user.get.id

      integrationUser = Await.result(integrationUsersDAO.findByUserId(user.get.id, "test-integration"), Duration.Inf)
      integrationUser.isDefined mustEqual true

      m = Await.result(integrationGroupsDAO.merge(IntegrationGroup("test-integration", "test-integration-group", user.get.id, "Test Integration Group")), Duration.Inf)
      m mustEqual true
      val integrationGroup = Await.result(integrationGroupsDAO.find("test-integration", "test-integration-group", user.get.id), Duration.Inf)
      integrationUser.isDefined mustEqual true

      m = Await.result(integrationGroupsDAO.merge(IntegrationGroup("test-integration", "test-integration-group", user.get.id, "Test Integration Group")), Duration.Inf)
      m mustEqual false

      m = Await.result(integrationTopicsDAO.merge(IntegrationTopic("test-integration", "test-integration-topic", "test-integration-group", user.get.id, "test-integration-user",
        new Timestamp(Calendar.getInstance.getTime.getTime), "Test integration topic", "Test integraiton topic title")), Duration.Inf)
      m mustEqual true

      var integrationTopic = Await.result(integrationTopicsDAO.find("test-integration", "test-integration-topic", user.get.id), Duration.Inf)
      integrationTopic.isDefined mustEqual true

      var integrationUpdateId = Await.result(integrationUpdatesDAO.insert(IntegrationUpdate(0, "test-integration", None, "test-integration-group",
        "test-integration-topic", user.get.id, "test-integration-user", new Timestamp(Calendar.getInstance.getTime.getTime), "Test integration update")), Duration.Inf)

      m = Await.result(integrationUpdatesDAO.merge(IntegrationUpdate(integrationUpdateId, "test-integration", Some("test-integration-update"), "test-integration-group",
        "test-integration-topic", user.get.id, "test-integration-user", new Timestamp(Calendar.getInstance.getTime.getTime), "Test integration update")), Duration.Inf)
      m mustEqual false

      var integrationUpdate = Await.result(integrationUpdatesDAO.find("test-integration", "test-integration-update", user.get.id), Duration.Inf)
      integrationUpdate.isDefined mustEqual true
    }
  }

  "User model" should {
    "work as expected" in new WithApplication(appWithMemoryDatabase()) {
        var test = Await.result(usersDAO.findByLogin("test-user"), Duration.Inf)
        if (test.isEmpty) {
          Await.result(usersDAO.insert(User(0, "test-user", "Test User", None)), Duration.Inf)
          test = Await.result(usersDAO.findByLogin("test-user"), Duration.Inf)
        }

        val users = Await.result(usersDAO.all, Duration.Inf)
        users.length mustEqual 1
    }
  }
}
