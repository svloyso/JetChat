package models

import javax.inject.{Inject, Singleton}

import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.Future

/**
  * Created by svloyso on 16.04.16.
  */

case class Bot(userId: Long, code: String, state: Option[String], isActive: Boolean)

trait BotsComponent extends HasDatabaseConfigProvider[JdbcProfile]
  with UsersComponent {
  protected val driver: JdbcProfile
  import driver.api._

  class BotsTable(tag: Tag) extends Table[Bot](tag, "bots") {
    def userId      = column[Long]    ("user_id", O.PrimaryKey)
    def code        = column[String]  ("code")
    def state       = column[Option[String]]  ("state")
    def isActive    = column[Boolean] ("is_active")
    def user = foreignKey("user_id", userId, allUsers)(_.id)
    def * = (userId, code, state, isActive) <> (Bot.tupled, Bot.unapply)
  }

  val allBots = TableQuery[BotsTable]
}

@Singleton()
class BotsDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile]
    with BotsComponent
    with UsersComponent {

  import driver.api._

  def findByUserId(userId: Long): Future[Option[Bot]] = {
    db.run(allBots.filter(_.userId === userId).result.headOption)
  }

  def insert(bot: Bot) = {
    db.run(allBots += bot)
  }

  def setActive(userId: Long, active: Boolean): Future[Int] = {
    db.run(allBots filter (_.userId === userId) map (_.isActive) update active)
  }

  def updateState(userId: Long, newState: String) = {
    db.run(allBots filter (_.userId === userId) map (_.state) update Some(newState))
  }

  def all(): Future[Seq[Bot]] = {
    db.run(allBots.result)
  }
}
