package org.bentocorp.db

import org.bentocorp.dispatch.Driver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import slick.driver.MySQLDriver.simple._
import slick.lifted.{Tag, TableQuery}

import scala.collection.JavaConversions._

class TDriver(tag: Tag) extends Table[(Long, Option[String], Option[String], Option[String], Option[String], Option[Byte],
  Option[String], Option[String], Option[String])](tag, "Driver") {
  def pk_Driver = column[Long]("pk_Driver", O.PrimaryKey, O.AutoInc)
  def firstname = column[Option[String]]("firstname")
  def lastname = column[Option[String]]("lastname")
  def mobile_phone = column[Option[String]]("mobile_phone")
  def email = column[Option[String]]("email")
  def status = column[Option[Byte]]("status")
  def password = column[Option[String]]("password")
  def api_token = column[Option[String]]("api_token")
  def order_queue = column[Option[String]]("order_queue")
  def * = (pk_Driver, firstname, lastname, mobile_phone, email, status, password, api_token, order_queue)
}

@Component
class DriverDao {

  @Autowired
  var db: Database = null

  val drivers = TableQuery[TDriver]

  def selectAll = db() withSession { implicit session =>
    drivers.map(r => (r.pk_Driver, r.firstname, r.lastname, r.mobile_phone, r.status, r.order_queue)).list
  }

  def updateOrderQueue(driverId: Long, queue: java.util.List[String]) = db() withSession { implicit session =>
    val row = for { d <- drivers if d.pk_Driver === driverId } yield d.order_queue
    row.update(Some(queue.mkString(",")))
  }

  def updateStatus(driverId: Long, status: Driver.Status) = db() withSession { implicit session =>
    val row = for { d <- drivers if d.pk_Driver === driverId } yield d.status
    row.update(Some(status.ordinal().toByte))
  }
}
