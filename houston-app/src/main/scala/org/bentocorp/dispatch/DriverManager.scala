package org.bentocorp.dispatch

import java.util.{TimeZone, Calendar}
import javax.annotation.PostConstruct

import org.bentocorp.db.DriverDao
import org.bentocorp.filter.ResyncInterceptor
import org.bentocorp.redis.{RMap, Redis}
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DriverManager {

  val Logger = LoggerFactory.getLogger(classOf[DriverManager])

  @Autowired
  var driverDao: DriverDao = null

  @Autowired
  var redis: Redis = null

  var drivers: RMap[Long, Driver] = null

  import org.bentocorp.Preamble._

  @PostConstruct
  def init() {
    drivers = redis.getMap[Long, Driver]("drivers")
    // Load data into redis if we are the first thread to reach here
    val resyncTs = ResyncInterceptor.getClosestResyncTimeMillis(System.currentTimeMillis)
    redis.race("DriverManager#init_" + resyncTs, () => {
      syncDrivers()
    })
  }

  import scala.collection.mutable.{Map => MMap}

  def syncDrivers() {
    Logger.info("Fetching drivers from database")
    val drivers = MMap.empty[Long, Driver]
    driverDao.selectAllActive foreach {
      case (pk, Some(firstname), Some(lastname), Some(phone), statusByte, queueStr) =>
        val normalizedPhone = normalize_phone(phone)
        val status = if (statusByte > 0) Driver.Status.ONLINE else Driver.Status.OFFLINE
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
    Logger.info("Processed %s driver(s)" format drivers.size)
    redis.setMap("drivers", drivers)
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
    try {
      redis.lock(driver.getLockId)
      // Try writing to database first
      if (driverDao.updateStatus(driverId, status) > 0) {
        driver.setStatus(status)
        // Then if successful, set to cache
        drivers += (driver.id -> driver)
      } else {
        throw new Exception("Error updating driver status in database")
      }
    } finally {
      redis.unlock(driver.getLockId)
    }
  }

  // Use DriverManager#removeOrder to remove an order from a driver's queue
  // Use OrderManager#unassign to atomically update order status AND remove the order from the driver's queue
  def removeOrder(driverId: Long, orderId: String) {
    val driver = getDriver(driverId)
    try {
      redis.lock(driver.getLockId)
      val orderQueue = driver.getOrderQueue
      if (!orderQueue.remove(orderId)) {
        throw new Exception("Error - Order %s not in order queue for driver %s" format (orderId, driverId))
      }
      // Try writing to database first
      driverDao.updateOrderQueue(driver.id, orderQueue)
      // Then update everything else
      driver.setOrderQueue(orderQueue)
      // Set to cache
      drivers += (driver.id -> driver)
    } finally {
      redis.unlock(driver.getLockId)
    }
  }
}
