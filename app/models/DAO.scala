package models

import myUtils.MyPostgresDriver
import play.api.db.slick.DB

class DAO(override val driver: MyPostgresDriver) extends UsersComponent with GroupsComponent with TopicsComponent with MessagesComponent {

  import driver.simple._

  val users = TableQuery(new UsersTable(_))
  val groups = TableQuery(new GroupsTable(_))
  val topics = TableQuery(new TopicsTable(_))
  val messages = TableQuery(new MessagesTable(_))
}

object current {
  val dao = new DAO(DB(play.api.Play.current).driver.asInstanceOf[MyPostgresDriver])
}
