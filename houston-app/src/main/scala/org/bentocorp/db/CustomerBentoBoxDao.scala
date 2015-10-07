package org.bentocorp.db

import slick.driver.MySQLDriver.simple._
import slick.lifted.Tag

class TCustomerBentoBox(tag: Tag) extends Table[(Long, Option[Long], Option[Long], Option[Long], Option[Long],
  Option[Long], Option[Long])](tag, "CustomerBentoBox") {
  def pk_CustomerBentoBox = column[Long]("pk_CustomerBentoBox", O.PrimaryKey, O.AutoInc)
  def fk_Order = column[Option[Long]]("fk_Order")
  def fk_main = column[Option[Long]]("fk_main")
  def fk_side1 = column[Option[Long]]("fk_side1")
  def fk_side2 = column[Option[Long]]("fk_side2")
  def fk_side3 = column[Option[Long]]("fk_side3")
  def fk_side4 = column[Option[Long]]("fk_side4")
  def * = (pk_CustomerBentoBox, fk_Order, fk_main, fk_side1, fk_side2, fk_side3, fk_side4)
}

class CustomerBentoBoxDao {

}
