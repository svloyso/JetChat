package models

import play.api.db.slick.DB

class DAO(override val driver: CustomDriver) extends UsersComponent with GroupsComponent with TopicsComponent with
CommentsComponent with DirectMessagesComponent {

  import driver.simple._

  val users = TableQuery(new UsersTable(_))
  val groups = TableQuery(new GroupsTable(_))
  val topics = TableQuery(new TopicsTable(_))
  val comments = TableQuery(new CommentsTable(_))
  val directMessages = TableQuery(new DirectMessagesTable(_))
}

object current {
  val dao = new DAO(DB(play.api.Play.current).driver.asInstanceOf[CustomDriver])
}
