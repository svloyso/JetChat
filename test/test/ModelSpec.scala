package test

import models.api.{IntegrationToken, IntegrationTokensDAO}
import models.{User, UsersDAO}
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

  "Integration model" should {
    "work as expected" in new WithApplication(appWithMemoryDatabase()) {
      var user = Await.result(usersDAO.findByLogin("test-user"), Duration.Inf)
      if (user.isEmpty) {
        Await.result(usersDAO.insert(User(0, "test-user", "Test User", None)), Duration.Inf)
        user = Await.result(usersDAO.findByLogin("test-user"), Duration.Inf)
      }

      var token = Await.result(integrationTokensDAO.find(user.get.id, "test-integration"), Duration.Inf)
      token.isDefined mustEqual false

      Await.result(integrationTokensDAO.merge(IntegrationToken(user.get.id, "test-integration", "test-token")), Duration.Inf)

      token = Await.result(integrationTokensDAO.find(user.get.id, "test-integration"), Duration.Inf)
      token.get.token mustEqual "test-token"

      Await.result(integrationTokensDAO.merge(IntegrationToken(user.get.id, "test-integration", "test-token-update")), Duration.Inf)

      token = Await.result(integrationTokensDAO.find(user.get.id, "test-integration"), Duration.Inf)
      token.get.token mustEqual "test-token-update"
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
