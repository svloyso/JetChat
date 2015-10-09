package models.api

import javax.inject.{Inject, Singleton}

import api.Integration
import models.UsersComponent
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.twirl.api.TemplateMagic.javaCollectionToScala
import slick.driver.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class IntegrationToken(userId: Long, integrationId: String, token: String)

trait IntegrationTokensComponent extends HasDatabaseConfigProvider[JdbcProfile] with UsersComponent {
  protected val driver: JdbcProfile

  import driver.api._

  class IntegrationTokensTable(tag: Tag) extends Table[IntegrationToken](tag, "integration_tokens") {
    def userId = column[Long]("user_id")

    def integrationId = column[String]("integration_id")
    def token = column[String]("token")

    def user = foreignKey("integration_token_user_fk", userId, users)(_.id)

    def tokenIndex = index("integration_token_index", (userId, integrationId), unique = true)

    def tokenUserIndex = index("integration_token_user_index", (userId), unique = false)

    def * = (userId, integrationId, token) <>(IntegrationToken.tupled, IntegrationToken.unapply)
  }

  val tokens = TableQuery[IntegrationTokensTable]
}

@Singleton()
class IntegrationTokensDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                                     val integrations: java.util.Set[Integration])
  extends HasDatabaseConfigProvider[JdbcProfile] with IntegrationTokensComponent with UsersComponent {

  import driver.api._

  def find(userId: Long, integrationId: String): Future[Option[IntegrationToken]] = {
    db.run(tokens.filter(t => t.userId === userId && t.integrationId === integrationId).result.headOption)
  }

  def find(userId: Long): Future[Map[String, Option[IntegrationToken]]] = {
    db.run(tokens.filter(t => t.userId === userId).result).map { case t =>
      integrations.toSeq.map { case i => i.id -> t.find(_.integrationId == i.id ) }.toMap
    }
  }

  def allTokens(integrationId: String): Future[Seq[IntegrationToken]] = {
    db.run { tokens.result }
  }

  def merge(token: IntegrationToken): Future[Boolean] = {
    find(token.userId, token.integrationId).flatMap {
      case None =>
        db.run(tokens += token).map(_ => true)
      case Some(existingToken) =>
        db.run(tokens.filter(t => t.userId === existingToken.userId && t.integrationId === existingToken.integrationId).map(_.token).update(token.token)).map(_ => false)
    }
  }
}
