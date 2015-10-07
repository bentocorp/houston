package org.bentocorp.dispatch

import javax.annotation.PostConstruct

import org.bentocorp.db.DriverDao
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DriverManager {

  val Logger = LoggerFactory.getLogger(classOf[DriverManager])

  @Autowired
  var driverDao: DriverDao = null

  import scala.collection.mutable
  // Initialize from database
  val drivers = mutable.Map.empty[Long, Driver]

  import org.bentocorp.Preamble._
  @PostConstruct
  def init() {
    Logger.info("Initializing drivers from database")
    driverDao.selectAll foreach {
      case (pk, Some(firstname), Some(lastname), Some(phone), Some(onshift), queueStr) =>
        val normalizedPhone = normalize_phone(phone)
        val status = if (onshift.toInt > 0) Driver.Status.ONLINE else Driver.Status.OFFLINE
        val driver = new Driver(pk, firstname + " " + lastname, normalizedPhone, status)
        queueStr match {
          case Some(str) =>
            val queue = new java.util.ArrayList[String]()
            str.split(",").filter(!_.isEmpty).foreach(queue.add)
            driver.setOrderQueue(queue)
          case _ =>
        }
        Logger.debug("Created " + driver.toString)
        drivers += pk -> driver
      case row =>
        throw new Exception("Bad driver row - " + row)
    }
  }

  @throws(classOf[Exception])
  def getDriver(driverId: Long): Driver = drivers.get(driverId) match {
    case Some(driver) => driver
    case None => throw new Exception("Driver %s not found" format driverId)
  }

  def isRegistered(driverId: Long) = drivers.contains(driverId)

  @throws(classOf[Exception])
  def setStatus(driverId: Long, status: Driver.Status) {
    val driver = getDriver(driverId)
    // Try writing to database first
    if (driverDao.updateStatus(driverId, status) > 0) {
      drivers(driverId).setStatus(status)
    } else {
      throw new Exception("Error updating driver status in database")
    }
  }

  // Use DriverManager#removeOrder to remove an order from a driver's queue
  // Use OrderManager#unassign to atomically update order status AND remove the order from the driver's queue
  def removeOrder(driverId: Long, orderId: String) {
    val driver = getDriver(driverId)
    try {
      driver.lock.writeLock().lock()
      val orderQueue = driver.getOrderQueue
      if (!orderQueue.remove(orderId)) {
        throw new Exception("Error - Order %s not in order queue for driver %s" format (orderId, driverId))
      }
      // Try writing to database first
      driverDao.updateOrderQueue(driver.id, orderQueue)
      // Then update everything else
      driver.setOrderQueue(orderQueue)
    } finally {
      driver.lock.writeLock().unlock()
    }
  }
}
