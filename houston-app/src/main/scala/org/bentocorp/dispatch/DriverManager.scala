package org.bentocorp.dispatch

import javax.annotation.PostConstruct

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.bentocorp.db.DriverDao
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import scala.collection.mutable.{Map => Map}

@Component
class DriverManager {

  val Logger = LoggerFactory.getLogger(classOf[DriverManager])

  val mapper = (new ObjectMapper).registerModule(DefaultScalaModule)

  @Autowired
  var driverDao: DriverDao = null

  @PostConstruct
  def init() {
    Logger.info("Initializing drivers from database")
    driverDao.selectAll foreach {
      case (pk, Some(firstname), Some(lastname), Some(phone), Some(onshift), queueStr) =>
        val normalizedPhone = phone.replaceAll("\\(|\\)|\\-|\\s", "")
        val status = if (onshift.toInt > 0) Driver.Status.ONLINE else Driver.Status.OFFLINE
        val driver = new Driver(pk, firstname + " " + lastname, normalizedPhone, status)
        queueStr match {
          case Some(str) =>
            val queue = new java.util.ArrayList[java.lang.Long]()
            Logger.debug(">>"+mapper.writeValueAsString(queue))
            str.split(",").filter(!_.isEmpty).map(orderId => new java.lang.Long(orderId)).foreach(queue.add)
            driver.setOrderQueue(queue)
          case _ =>
        }
        Logger.debug("Created " + driver.toString)
        drivers += pk -> driver
      case row =>
        throw new Exception("Bad driver row - " + row)
    }
  }

  // Initialize from database
  val drivers = Map.empty[Long, Driver]

  @throws(classOf[Exception])
  def apply(driverId: Long): Driver = drivers.get(driverId) match {
    case Some(driver) => driver
    case None => throw new Exception("Driver %s not found" format driverId)
  }

  def isRegistered(driverId: Long) = drivers.contains(driverId)

  @throws(classOf[Exception])
  def setStatus(driverId: Long, status: Driver.Status) {
    if (driverDao.updateStatus(driverId, status) > 0) {
      drivers(driverId).setStatus(status)
    } else {
      throw new Exception("Error updating driver status in database")
    }
  }

  @throws(classOf[Exception])
  def removeOrder(driverId: Long, orderId: Long) {
    val driver = this(driverId)
    val orderQueue = driver.getOrderQueue
    if (orderQueue.remove(new java.lang.Long(orderId))) {
      if (driverDao.updateOrderQueue(driverId, orderQueue) > 0) {
        driver.setOrderQueue(orderQueue)
      } else {
        throw new Exception("Error updating database while trying to remove order %s for driver %s" format (orderId, driverId))
      }
    } else {
      throw new Exception("Error removing order %s from in cache order queue for driver %s" format (orderId, driverId))
    }
  }
}
