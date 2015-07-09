package models

import myUtils.{MyPostgresDriver, WithMyDriver}

case class Group(id: String)

trait GroupsComponent extends WithMyDriver {

  import driver.simple._

  class GroupsTable(tag: Tag) extends MyPostgresDriver.Table[Group](tag, "Group") {
    def id = column[String]("id", O.PrimaryKey)

    def * = id <>(Group.apply, Group.unapply)
  }

}