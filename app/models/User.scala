package models

import myUtils.{MyPostgresDriver, WithMyDriver}

case class User(id: Long = 0, login: String, name: String, avatar: Option[String])

trait UsersComponent extends WithMyDriver {

  import driver.simple._

  class UsersTable(tag: Tag) extends MyPostgresDriver.Table[User](tag, "User") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def login = column[String]("login", O.NotNull)
    def name = column[String]("name", O.NotNull)
    def avatar = column[Option[String]]("avatar", O.Nullable)

    def * = (id, login, name, avatar) <>(User.tupled, User.unapply)
  }

}
