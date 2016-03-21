package models

import javax.inject.{Inject, Singleton}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// TODO: Make IntegrationUser.name Option[String]
case class IntegrationUser(integrationId: String, userId: Option[Long], integrationUserId: String,
                           name: String, avatar: Option[String])

trait IntegrationUsersComponent extends HasDatabaseConfigProvider[JdbcProfile] {
  protected val driver: JdbcProfile

  import driver.api._

  class IntegrationUsersTable(tag: Tag) extends Table[IntegrationUser](tag, "integration_users") {
    def integrationId = column[String]("integration_id")
    def userId = column[Option[Long]]("user_id")
    def integrationUserId = column[String]("integration_user_id")
    def integrationUserName = column[String]("integration_user_name")
    def integrationUserAvatar = column[Option[String]]("integration_user_avatar")
    def integrationUserIdIndex = index("integration_user_id_index", (integrationId, userId), unique = false)
    def pk = index("integration_integration_user_id_index", (integrationId, integrationUserId), unique = true)
    def * = (integrationId, userId, integrationUserId, integrationUserName, integrationUserAvatar) <>
      (IntegrationUser.tupled, IntegrationUser.unapply)
  }

  val allIntegrationUsers = TableQuery[IntegrationUsersTable]
}

@Singleton()
class IntegrationUsersDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationUsersComponent {

  import driver.api._

  def allUsers(integrationId: String): Future[Seq[IntegrationUser]] = {
    db.run { allIntegrationUsers.result }
  }

  def findByUserId(userId: Long, integrationId: String): Future[Option[IntegrationUser]] = {
    db.run(allIntegrationUsers.filter(u => u.userId === userId && u.integrationId === integrationId).result.headOption)
  }

  def findByIntegrationUserId(integrationUserId: String, integrationId: String): Future[Option[IntegrationUser]] = {
    db.run(allIntegrationUsers.filter(u => u.integrationUserId === integrationUserId && u.integrationId === integrationId).result.headOption)
  }

  def merge(user: IntegrationUser): Future[Boolean] = {
    findByIntegrationUserId(user.integrationUserId, user.integrationId).flatMap {
      case None =>
        db.run(allIntegrationUsers += user).map(_ => true)
      case Some(existingUser) =>
        if (user.userId.isDefined && existingUser.userId.isEmpty) {
          db.run(allIntegrationUsers.filter(u => u.integrationUserId === existingUser.integrationUserId && u.integrationId === existingUser.integrationId).map(_.userId).update(user.userId)).map(_ => false)
        } else Future {
          false
        }
    }
  }
}
