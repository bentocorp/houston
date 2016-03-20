package org.bentocorp.db

import java.sql.{Connection, Timestamp}
import java.util

import org.bentocorp.Order
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import scala.collection.mutable.ListBuffer
import scala.slick.driver.MySQLDriver.simple._
import scala.slick.lifted.{TableQuery, Tag}

class TOrder(tag: Tag) extends Table[(Long, Option[Long], Option[Timestamp], Option[String], Option[String],
        Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[Int],
        Option[Timestamp], Option[Timestamp], Option[String])](tag, "Order") {

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

    def order_type = column[Option[Int]]("order_type")

    def scheduled_window_start = column[Option[Timestamp]]("utc_scheduled_window_start")

    def scheduled_window_end = column[Option[Timestamp]]("utc_scheduled_window_end")

    def scheduled_timezone = column[Option[String]]("scheduled_timezone")

    def * = (pk_Order, fk_User, created_at, number, street, city, state, zip, lat, long, notes_for_driver, order_type,
            scheduled_window_start, scheduled_window_end, scheduled_timezone)
}

object OrderDao {
    type CompleteOrderRow = (Long, Option[Timestamp], Option[String], Option[String], Option[String], Option[String], Option[String], /*Some(city), Some(state),*/
            Option[String], Long, Option[String], Option[Long], Option[Long], Option[Long], Option[Long], Option[Long], Option[String],
            Option[Long], Option[String], Option[String], Option[Int], Option[Timestamp], Option[Timestamp])
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

    // Helper function that returns a Query object for joining multiple tables, resulting in rows that completely describe
    // a Bento order
    private def _join = {
        for {
            o <- order
            b <- customerBentoBox
            u <- user
            s <- status
            if o.pk_Order === b.fk_Order && o.fk_User === u.pk_User && o.pk_Order === s.fk_Order
        } yield {
            (
                // Eventually, must use Slick's HList to overcome Scala's 22-Tuple limit
                o.pk_Order,
                o.created_at, // 2
                u.firstname,
                u.lastname,
                u.phone,
                o.number.getOrElse("") ++ " " ++ o.street,
                o.scheduled_timezone,
                //      o.city, // Ignore city for now (always San Francisco) to avoid limit
                //      o.state, // Ignore state for now (always California) to avoid limit
                o.city ++ "," ++ o.state ++ "," ++ o.zip,
                //o.zip,
                u.pk_User,
                o.lat ++ "," ++ o.long,
                //o.long,
                b.fk_main,
                b.fk_side1,
                b.fk_side2,
                b.fk_side3,
                b.fk_side4, // TODO - Remove after we release 4-pod Bentos
                s.status, // 16
                s.fk_Driver,
                o.notes_for_driver,
                s.driver_text_blob,
                o.order_type,
                o.scheduled_window_start, // 21
                o.scheduled_window_end // 22
            )
        }
    }

    // Select all "active" orders & all "closed" orders after <param>start</param>
    // TODO - Very inefficient because we are still doing a full table scan to retrieve all active orders. Might be better
    // to ignore all active orders before <param>start</param>
    def select(start: Timestamp,
               // Slick converts None into SQL type NULL
               // Do not use null otherwise you will get a NullPointerException
               optionalEnd: Option[Timestamp] = None) = database() withSession { implicit session =>
        // Since we now support Order Ahead, we must simultaneously fetch 2 kinds of orders
        //   a) On-Demand orders
        // Therefore, internally, we require the end parameter and if is None, we will default it to 24 hours after start
        val end = optionalEnd getOrElse {
            new Timestamp(start.getTime + 86400000)
        }
        val res = _join filter { r =>
            // Look at scheduled windows first and take all orders that need to be delivered on or after <param>start</param>
            // and before <param>end</param>
            (r._21 >= start && r._22 <= end) ||
                    // Then take all On-Demand orders created on or after @param{start} and before @param{end}
                    (r._21.isEmpty && r._22.isEmpty && r._2 >= start && r._2 < end) /*||
      // Open orders outstanding
      // XXX - This last clause may be computationally expensive because the tables are not indexed on order status
      (r._16 =!= Order.Status.CANCELLED.toString && r._16 =!= Order.Status.COMPLETE.toString && (r._22 < end || r._2 < end))*/
        }
        res.list
    }

    def selectByDeliveryWindow(start: Timestamp, end: Timestamp) = database() withSession { implicit session =>
        // Implies Order-Ahead
        val res = _join filter { r =>
            r._21 >= start && r._22 <= end
        }
        //println(res.selectStatement)
        res.list
    }


    def selectByPrimaryKey(pk: Long) = database() withSession { implicit session =>
        _join filter (_._1 === pk) list
    }

    def updateStatus(orderId: Long, orderStatus: Order.Status): Int = database() withSession { implicit session =>
        val row = for {s <- status if s.fk_Order === orderId} yield s.status
        row.update(Some(orderStatus.toString))
    }


    def getOrderAddons(start: Timestamp, end: Timestamp) =
    {
        var con: Connection = null
        try {
            // Try to obtain a connection from the pool
            con = database.getDataSource.getConnection

            // Turn off auto-commit to start a transaction
            //con.setAutoCommit(false)

            val resultSet = con.createStatement().executeQuery(
                """
                  |SELECT oi.*, d.*
                  |FROM `bento`.`OrderItem` oi
                  |left join `Order` o on (oi.fk_Order = o.pk_Order)
                  |left join Dish d on (oi.fk_item = d.pk_Dish)
                  |where
                  |	order_type = 2
                  |    AND utc_scheduled_window_start >= '%s'
                  |    AND utc_scheduled_window_end <= '%s'
                  |AND oi.item_type = 'Addon';""".stripMargin
                    format(start, end)
            )

            println(start.toString)
            println(end.toString)

            val rows = new util.ArrayList[(Long, Long, Long, Int, String)]()

            while (resultSet.next())
            {
                rows.add( (
                        resultSet.getLong("pk_OrderItem"),
                        resultSet.getLong("fk_Order"),
                        resultSet.getLong("fk_item"),
                        resultSet.getInt("qty"),
                        resultSet.getString("name")
                ) )
            }

            rows

        } catch {
            case e: Exception =>
                val stackTrace = Thread.currentThread.getStackTrace
                Logger.error("Error - assignOrderTransaction - " + e.getMessage, stackTrace)
                // If we encounter an error, rollback all changes
                //con.rollback()
                throw e
        } finally {
            // Important that we release all resources
            if (con != null) con.close()
        }
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
                "UPDATE `OrderStatus` SET `status`='%s', `fk_Driver`=%s WHERE `fk_Order`=%s" format(status.toString, driverId, args[Long]("pk_Order")),
                "UPDATE `Driver` SET `order_queue`='%s' WHERE `pk_Driver`=%s" format(args[String]("order_queue"), args[Long]("pk_Driver"))
            )
            sqls foreach { sql =>
                val rowsAffected = con.prepareStatement(sql).executeUpdate()
                if (rowsAffected <= 0) {
                    throw new Exception("Error - rows affected = %s for statement %s" format(rowsAffected, sql))
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
