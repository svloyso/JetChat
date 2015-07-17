package models

case class Group(id: Long = 0, name: String)

trait GroupsComponent extends WithMyDriver {

  import driver.simple._

  class GroupsTable(tag: Tag) extends CustomDriver.Table[Group](tag, "groups") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name", O.NotNull)

    def nameIndex = index("group_name_index", name, unique = true)

    def * = (id, name) <>(Group.tupled, Group.unapply)
  }

}