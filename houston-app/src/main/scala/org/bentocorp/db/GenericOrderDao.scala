package org.bentocorp.db

import java.sql.{Connection, Timestamp}

import org.bentocorp.Order
import org.bentocorp.dispatch.Address
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import slick.driver.MySQLDriver.simple._
import slick.lifted.{Tag, TableQuery}

class TGenericOrder(tag: Tag) extends Table[(Option[Long], Timestamp, Option[Timestamp], Option[Timestamp], String,
  Option[Long], Option[String], Option[String], Option[String], Option[String], Option[String],
  Option[String], Option[String], Option[String], Option[String], Option[String])](tag, "generic_Order") {
  def pk_generic_Order = column[Option[Long]]("pk_generic_Order", O.PrimaryKey, O.AutoInc)
  def created_at = column[Timestamp]("created_at", O.NotNull)
  def updated_at = column[Option[Timestamp]]("updated_at")
  def deleted_at = column[Option[Timestamp]]("deleted_at")
  def status = column[String]("status")
  def fk_Driver = column[Option[Long]]("fk_Driver")
  def name = column[Option[String]]("name")
  def phone = column[Option[String]]("phone")
  def street = column[Option[String]]("street")
  def city = column[Option[String]]("city")
  def region = column[Option[String]]("region")
  def zip_code = column[Option[String]]("zip_code")
  def country = column[Option[String]]("country")
  def lat = column[Option[String]]("lat")
  def lng = column[Option[String]]("lng")
  def body = column[Option[String]]("body")
  def * = (pk_generic_Order, created_at, updated_at, deleted_at, status, fk_Driver, name, phone, street,
    city, region, zip_code, country, lat, lng, body)
}

@Component
class GenericOrderDao {

  final val Logger = LoggerFactory.getLogger(classOf[GenericOrderDao])

  @Autowired
  val database: Database = null

  val genericOrders = TableQuery[TGenericOrder]

  def select(day: Timestamp) = database() withSession { implicit session =>
    genericOrders.filter(_.created_at >= day).map(r => (
      r.pk_generic_Order,
      r.status,
      r.fk_Driver,
      r.name,
      r.phone,
      r.street,
      r.city,
      r.region,
      r.zip_code,
      r.country,
      r.lat,
      r.lng,
      r.body
    )).list
  }

  def insert(values: Database.Map): Order[String] = database() withSession { implicit session =>
    val createdAt = new Timestamp(System.currentTimeMillis)
    val driverId = values.get[Long]("fk_Driver")
    val name = values.get[String]("name")
    val phone = values.get[String]("phone")
    val street = values.get[String]("street")
    val city = values.get[String]("city")
    val region = values.get[String]("region")
    val zipCode = values.get[String]("zip_code")
    val country = values.get[String]("country")
    val lat = values.get[String]("lat")
    val lng = values.get[String]("lng")
    val body = values.get[String]("body")
    // Try writing to the database first
    val orderId = (genericOrders returning genericOrders.map(_.pk_generic_Order)) += (None, createdAt, None, None,
      Order.Status.UNASSIGNED.toString, driverId, name, phone, street, city, region, zipCode, country,
      lat, lng, body)
    // If successful, return a new Order instance
    // TODO - implied non-nullable parameters not reflected in database schema
    val address = new Address(street.get, null, city.get, region.get, zipCode.get, country.get)
    val order = new Order[String]("g-" + orderId.get, name.get, phone.get, address, body.get)
    order
  }

  def updateStatus(orderId: Long, orderStatus: Order.Status): Int = database() withSession { implicit session =>
    val row = for { o <- genericOrders if o.pk_generic_Order === orderId } yield o.status
    row.update(orderStatus.toString)
  }

  // Transaction to assign/unassign Bento orders to drivers
  def assignOrderTransaction(args: Database.Map, status: Order.Status) {
    var con: Connection = null
    try {
      // Try to obtain a connection from the pool
      con = database.getDataSource.getConnection
      // Turn off auto-commit to start a transaction
      con.setAutoCommit(false)
      // To mitigate deadlocks, we must enforce a natural ordering on the resources we need to obtain - in this case,
      // the database columns we want to modify.
      con.prepareStatement(
        "SELECT `status`, `fk_Driver` FROM `generic_Order` WHERE `pk_generic_Order`=%s FOR UPDATE;" format args[Long]("pk_generic_Order")
      ).execute()
      con.prepareStatement(
        "SELECT `order_queue` FROM `Driver` WHERE `pk_Driver`=%s FOR UPDATE;" format args[Long]("pk_Driver")
      ).execute()
      // If status is Order.Status.ASSIGNED, the order will also be assigned to the driver
      val driverId = status match {
        case Order.Status.PENDING => args[Long]("pk_Driver")
        case Order.Status.UNASSIGNED => -1L
        case Order.Status.CANCELLED => -1L
        case _ => throw new Exception("Error - assignOrderTransaction - Unsupported order status " + status)
      }
      // Update SQL statements
      val sqls = Array(
        "UPDATE `generic_Order` SET `status`='%s', `fk_Driver`=%s WHERE `pk_generic_Order`=%s" format (status.toString, driverId, args[Long]("pk_generic_Order")),
        "UPDATE `Driver` SET `order_queue`='%s' WHERE `pk_Driver`=%s" format (args[String]("order_queue"), args[Long]("pk_Driver"))
      )
      sqls foreach { sql =>
        val rowsAffected = con.prepareStatement(sql).executeUpdate()
        if (rowsAffected <= 0) {
          throw new Exception("Error - rows affected = %s for statement %s" format (rowsAffected, sql))
        }
      }
      con.commit()
    } catch {
      case e: Exception =>
        val stackTrace = Thread.currentThread.getStackTrace
        Logger.error("Error - assignOrderTransaction - " + e.getMessage, stackTrace)
        // If we encounter an error, rollback all changes
        con.rollback()
        throw e
    } finally {
      // Important that we release all resources
      if (con != null) con.close()
    }
  }
}
