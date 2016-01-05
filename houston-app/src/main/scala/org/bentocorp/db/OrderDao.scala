package org.bentocorp.db

import java.sql.{Connection, Timestamp}

import org.bentocorp.Order
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import slick.driver.MySQLDriver.simple._
import slick.lifted.{Tag, TableQuery}

class TOrder(tag: Tag) extends Table[(Long, Option[Long], Option[Timestamp], Option[String], Option[String],
  Option[String], Option[String], Option[String], Option[String], Option[String], Option[String])](tag, "Order") {

  def pk_Order = column[Long]("pk_Order", O.PrimaryKey, O.AutoInc)
  def fk_User = column[Option[Long]]("fk_User")
  def created_at = column[Option[Timestamp]]("created_at")
  def number = column[Option[String]]("number")
  def street = column[Option[String]]("street")
  def city = column[Option[String]]("city")
  def state = column[Option[String]]("state")
  def zip = column[Option[String]]("zip")
  def lat = column[Option[String]]("lat")
  def long = column[Option[String]]("long")
  def notes_for_driver = column[Option[String]]("notes_for_driver")
  def * = (pk_Order, fk_User, created_at, number, street, city, state, zip, lat, long, notes_for_driver)
}

@Component
class OrderDao extends Updatable("Order") {

  final val Logger = LoggerFactory.getLogger(classOf[OrderDao])

  @Autowired
  var database: Database = null

  val order = TableQuery[TOrder]
  val customerBentoBox = TableQuery[TCustomerBentoBox]
  val user = TableQuery[TUser]
  val status = TableQuery[TOrderStatus]

  // Select all "active" orders & all "closed" orders after <param>day</param>
  // TODO note - very inefficient! make sure we come back to address this
  def select(day: Timestamp) = database() withSession { implicit session =>
    val res =
      for {
        o <- order
        b <- customerBentoBox
        u <- user
        s <- status
        if o.pk_Order === b.fk_Order && o.fk_User === u.pk_User && o.pk_Order === s.fk_Order &&
          ((s.status =!= Order.Status.CANCELLED.toString && s.status =!= Order.Status.COMPLETE.toString) || o.created_at >= day)
      } yield {(
        o.pk_Order,
        u.firstname,
        u.lastname,
        u.phone,
        o.number,
        o.street,
        o.city,
        o.state,
        o.zip,
        o.lat,
        o.long,
        b.fk_main,
        b.fk_side1,
        b.fk_side2,
        b.fk_side3,
        b.fk_side4,
        s.status,
        s.fk_Driver,
        o.notes_for_driver,
        s.driver_text_blob
      )}
    res.list
  }

  def updateStatus(orderId: Long, orderStatus: Order.Status): Int = database() withSession { implicit session =>
    val row = for { s <- status if s.fk_Order === orderId } yield s.status
    row.update(Some(orderStatus.toString))
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
        "SELECT `status`, `fk_Driver` FROM `OrderStatus` WHERE `fk_Order`=%s FOR UPDATE;" format args[Long]("pk_Order")
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
        "UPDATE `OrderStatus` SET `status`='%s', `fk_Driver`=%s WHERE `fk_Order`=%s" format (status.toString, driverId, args[Long]("pk_Order")),
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
