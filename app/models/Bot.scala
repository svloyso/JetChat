package models

import javax.inject.{Inject, Singleton}

import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.Future

/**
  * Created by svloyso on 16.04.16.
  */

case class Bot(id: Long = 0, userId: Long, name: String, code: String, isActive: Boolean)

trait BotsComponent extends HasDatabaseConfigProvider[JdbcProfile]
  with UsersComponent {
  protected val driver: JdbcProfile
  import driver.api._

  class BotsTable(tag: Tag) extends Table[Bot](tag, "bots") {
    def id          = column[Long]    ("id", O.PrimaryKey, O.AutoInc)
    def userId      = column[Long]    ("user_id")
    def name        = column[String]  ("name")
    def code        = column[String]  ("code")
    def isActive    = column[Boolean] ("is_active")
    def user = foreignKey("user_id", userId, allUsers)(_.id)
    def * = (id, userId, name, code, isActive) <> (Bot.tupled, Bot.unapply)
  }

  val allBots = TableQuery[BotsTable]
}

@Singleton()
class BotsDAO @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile]
    with BotsComponent
    with UsersComponent {

  import driver.api._

  def findById(id: Long): Future[Option[Bot]] = {
    db.run(allBots.filter(_.id === id).result.headOption)
  }

  def findByUserId(userId: Long): Future[Option[Bot]] = {
    db.run(allBots.filter(_.userId === userId).result.headOption)
  }

  def findByName(name: String): Future[Option[Bot]] = {
    db.run(allBots.filter(_.name === name).result.headOption)
  }

  def insert(bot: Bot): Future[Long] = {
    db.run((allBots returning allBots.map(_.id)) += bot)
  }

  def setActive(id: Long, active: Boolean): Future[Int] = {
    db.run(allBots filter (_.id === id) map (_.isActive) update active)
  }

  def all(): Future[Seq[Bot]] = {
    db.run(allBots.result)
  }
}
