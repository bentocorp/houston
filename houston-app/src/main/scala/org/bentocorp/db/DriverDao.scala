package org.bentocorp.db

import java.sql.Timestamp

import org.bentocorp.dispatch.Driver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import slick.driver.MySQLDriver.simple._
import slick.lifted.{Tag, TableQuery}

import scala.collection.JavaConversions._

class TDriver(tag: Tag) extends Table[(Long, Option[String], Option[String], Option[String], Option[String], Byte,
  Option[String], Option[String], Option[String], Option[Byte])](tag, "Driver") {

  def pk_Driver = column[Long]("pk_Driver", O.PrimaryKey, O.AutoInc)

  def deleted_at = column[Option[Timestamp]]("deleted_at")

  def firstname = column[Option[String]]("firstname")

  def lastname = column[Option[String]]("lastname")

  def mobile_phone = column[Option[String]]("mobile_phone")

  def email = column[Option[String]]("email")

  // driver's status with respect to the node server
  def status = column[Byte]("status")

  def password = column[Option[String]]("password")

  def api_token = column[Option[String]]("api_token")

  def order_queue = column[Option[String]]("order_queue")

  // A flag that is set via the admin dashboard which indicates which drivers are working
  def on_shift = column[Option[Byte]]("on_shift")

  def * = (pk_Driver, firstname, lastname, mobile_phone, email, status, password, api_token, order_queue, on_shift)
}

@Component
class DriverDao {

  @Autowired
  var db: Database = null

  val drivers = TableQuery[TDriver]

  def selectAllActive = db() withSession { implicit session =>
    drivers
      .filter(r => r.deleted_at.isEmpty)
      .map(r => (r.pk_Driver, r.firstname, r.lastname, r.mobile_phone, r.status, r.order_queue)).list
  }

  def updateOrderQueue(driverId: Long, queue: java.util.List[String]) = db() withSession { implicit session =>
    val row = for { d <- drivers if d.pk_Driver === driverId } yield d.order_queue
    row.update(Some(queue.mkString(",")))
  }

  def updateStatus(driverId: Long, status: Driver.Status) = db() withSession { implicit session =>
    val row = for { d <- drivers if d.pk_Driver === driverId } yield d.status
    row.update(status.ordinal().toByte)
  }
}
