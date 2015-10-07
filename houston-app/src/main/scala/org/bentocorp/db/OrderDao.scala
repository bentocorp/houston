package org.bentocorp.db

import java.sql.{Connection, Timestamp}

import org.bentocorp.Order
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import slick.driver.MySQLDriver.simple._
import slick.lifted.{Tag, TableQuery}

class TOrder(tag: Tag) extends Table[(Long, Option[Long], Option[Timestamp], Option[String], Option[String],
  Option[String], Option[String], Option[String], Option[String], Option[String])](tag, "Order") {
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
  def * = (pk_Order, fk_User, created_at, number, street, city, state, zip, lat, long)
}

@Component
class OrderDao {

  final val Logger = LoggerFactory.getLogger(classOf[OrderDao])

  @Autowired
  var db: Database = null

  val order = TableQuery[TOrder]
  val customerBentoBox = TableQuery[TCustomerBentoBox]
  val user = TableQuery[TUser]
  val status = TableQuery[TOrderStatus]

  def selectAll(day: Timestamp) = db() withSession { implicit session =>
    val res =
      for {
        o <- order
        b <- customerBentoBox
        u <- user
        s <- status
        if o.pk_Order === b.fk_Order && o.fk_User === u.pk_User && o.pk_Order === s.fk_Order && o.created_at >= day
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
        s.fk_Driver
      )}
    res.list
  }

  def updateStatus(orderId: Long, orderStatus: Order.Status): Int = db() withSession { implicit session =>
    val row = for { s <- status if s.fk_Order === orderId } yield s.status
    row.update(Some(orderStatus.toString))
  }

  @throws(classOf[Exception])
  def assignTransaction(orderId: Long, orderStatus: org.bentocorp.Order.Status, assignedDriverId: java.lang.Long, driverId: Long, orderQueue: String) = {
    var con: Connection = null
    try {
      con = db.getDataSource.getConnection
      con.setAutoCommit(false)
      // To minimize deadlock, we need to enforce a natural ordering on the resources we want to lock (in this case,
      // the database columns) - order, currently assigned driver, newly assigned driver (if any)
      // TODO - Do we have an index on fk_Order for OrderStatus?
      con.prepareStatement(
        "SELECT `status`, fk_Driver, order_queue FROM OrderStatus, Driver WHERE fk_Order=%s AND pk_Driver=%s FOR UPDATE;"
        format (orderId, driverId)
      ).execute()
      // Perform updates
      val sqls = Array(
        "UPDATE OrderStatus SET `status`='%s', fk_Driver=%s WHERE fk_Order=%s;" format (orderStatus.toString, assignedDriverId, orderId),
        "UPDATE Driver SET order_queue='%s' WHERE pk_Driver=%s;" format (orderQueue, driverId)
      )
      sqls foreach { sql =>
        println(sql)
        val rowsAffected = con.prepareStatement(sql).executeUpdate()
        if (rowsAffected <= 0) {
          throw new Exception("Error - rows affected = %s for statement %s" format (rowsAffected, sql))
        }
      }
      // TODO - Do all locks get released after committing, or only rows that have been updated?
      con.commit()
    } catch {
      case e: Exception =>
        Logger.error("Error executing assign transaction - rolling back")
        e.printStackTrace() // Configure to print to error logs?
        con.rollback()
        throw e
    } finally {
      if (con != null) con.close()
    }
  }
}
