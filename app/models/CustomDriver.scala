package models

import scala.slick.driver.JdbcDriver

trait WithMyDriver {
  val driver: CustomDriver
}

trait CustomDriver extends JdbcDriver with slick.driver.MySQLDriver

object CustomDriver extends CustomDriver