package org.bentocorp.db

import org.bentocorp.Order
import org.springframework.beans.factory.annotation.Autowired

import slick.driver.MySQLDriver.simple._
import slick.lifted.{Tag, TableQuery}

class TOrderStatus(tag: Tag) extends Table[(Option[Long], Option[Long], Option[String], Option[String])](tag, "OrderStatus") {
  def fk_Order = column[Option[Long]]("fk_Order")
  def fk_Driver = column[Option[Long]]("fk_Driver")
  def status = column[Option[String]]("status")
  def driver_text_blob = column[Option[String]]("driver_text_blob")
  def * = (fk_Order, fk_Driver, status, driver_text_blob)
}

class OrderStatusDao {

  @Autowired
  var db: Database = null

  val orderStatus = TableQuery[TOrderStatus]

  def updateStatus(orderId: Long, status: String) = db() withSession { implicit session =>
    val s = for { s <- orderStatus if s.fk_Order === orderId } yield s.status
    s.update(Some(status))
  }

  def updateDriver(orderId: Long, driverId: Long) = db() withSession { implicit session =>
    val driver = for { s <- orderStatus if s.fk_Order === orderId } yield s.fk_Driver
    driver.update(Some(driverId))
  }

  def update(orderId: Long, status: Order.Status, driverId: Long) = db() withSession { implicit session =>
    val row = for { s <- orderStatus if s.fk_Order === orderId } yield (s.status, s.fk_Driver)
    row.update((Some(status.toString), Some(driverId)))
  }
}
