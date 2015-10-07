package org.bentocorp.dispatch

import java.sql.Timestamp
import javax.annotation.PostConstruct

import org.bentocorp.{BentoBox, Order}
import org.bentocorp.db._
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import scala.collection.mutable.{Map => Map, ListBuffer => List}

import scala.collection.JavaConversions._

@Component
class OrderManager {

  final val Logger = LoggerFactory.getLogger(classOf[OrderManager])

  final val MillisecondsPerDay = 1000 * 60 * 60 * 24

  @Autowired
  var driverDao: DriverDao = _

  @Autowired
  var dishDao: DishDao = _

  @Autowired
  var orderDao: OrderDao = _

  @Autowired
  var driverManager: DriverManager = null

  var dishes = Map.empty[Long, BentoBox.Item]

  val orders = Map.empty[Long, Order[List[BentoBox]]]

  @PostConstruct
  def init() {
    /* Menu */
    Logger.debug("Fetching menu items")
    dishDao.selectAll foreach (_ match {
      case (id, Some(name), Some(dishType), Some(label)) =>
        dishes += id -> new BentoBox.Item(id, name, dishType, label)
      case row =>
        Logger.error("Bad dish row - " + row)
    })
    /* Orders */
    val today = new Timestamp((System.currentTimeMillis / MillisecondsPerDay).asInstanceOf[Long] * 1000)
    val orderRows = orderDao.selectAll(today)
    Logger.info("Fetched %s orders created on or after %s" format (orderRows.size, today))
    orderRows foreach {
      case (orderId, Some(firstname), Some(lastname), Some(phone), Some(number), Some(street), Some(city), Some(state), Some(zipCode), Some(lat), Some(lng), Some(main), Some(side1), Some(side2), Some(side3), Some(side4), Some(statusStr), driverIdOpt) =>
        val status = Order.Status.parse(statusStr)
        if (status == Order.Status.CANCELLED) {
          Logger.debug("Ignoring %s order %s" format (status, orderId))
          return
        }
        val order = {
          orders.get(orderId) match {
            case Some(existingOrder) => existingOrder
            case _ =>
              val address = new Address(number + " " + street, null, city, state, zipCode, "United States")
              address.lat = lat.toFloat
              address.lng = lng.toFloat
              val newOrder = new Order[List[BentoBox]](orderId, firstname + " " + lastname, phone, address, List.empty[BentoBox])
              val driverId = if (driverIdOpt.isDefined && driverIdOpt.get > 0) new java.lang.Long(driverIdOpt.get) else null
              newOrder.setDriverIdWithStatus(driverId, status)
              orders += orderId -> newOrder
              newOrder
          }
        }
        val bentoBox = (new BentoBox).add(dishes(main)).add(dishes(side1)).add(dishes(side2)).add(dishes(side3)).add(dishes(side4))
        // We don't need to use a write lock here because the order item generally won't be modified by dispatcher threads
        order.item += bentoBox
      case row =>
        throw new Exception("Bad order row - " + row)
    }
  }

  @throws(classOf[Exception])
  def apply(orderId: Long): Order[List[BentoBox]] = orders.get(orderId) match {
    case Some(order) => order
    case None => throw new Exception("Order %s not found" format orderId)
  }

  @throws(classOf[Exception])
  def updateStatus(orderId: Long, status: Order.Status) = {
    val order = this(orderId)
    if (orderDao.updateStatus(orderId, status) > 0) {
      order.setStatus(status)
    } else {
      throw new Exception("Error updating status for order %s to %s" format (orderId, status))
    }
  }

  @throws(classOf[Exception])
  def assign(orderId: Long, driverId: Long, afterId: Long = -1) {
    // First, make sure the order and the driver exist
    val order = this(orderId)
    val driver = driverManager(driverId)
    try {
      // Lock resources in natural order -
      order.lock.writeLock().lock(); driver.lock.writeLock().lock()
      // Check if order already assigned
      val cd = order.getDriverId
      if (cd != null && cd > 0) {
        throw new Exception("Error - Order %s is currently assigned to driver %s" format (orderId, cd))
      }
      val q = driver.getOrderQueue
      if (afterId < 0) {
        q.add(orderId)
      } else {
        val pos = q.indexOf(afterId)
        if (pos < 0) {
          throw new Error("Error - could not find afterId %s in queue" format afterId)
        }
        q.add(pos, orderId)
      }
      // Try updating database first
      orderDao.assignTransaction(orderId, Order.Status.PENDING, driverId, driverId, q.mkString(","))
      // If this executes successfully (without an Exception thrown), update everything else
      order.setDriverIdWithStatus(driverId, Order.Status.PENDING)
      driver.setOrderQueue(q)
    } finally {
      // Always release write locks; note reverse natural order for unlocking
      driver.lock.writeLock().unlock(); order.lock.writeLock().unlock()
    }
  }

  @throws(classOf[Exception])
  def unassign(orderId: Long) {
    // First, make sure the order and driver exist
    val order = this(orderId)
    val driver = driverManager(order.getDriverId)
    try {
      println("un-assigning - order=%s, driver=%s" format (order.id, driver.id))
      order.lock.writeLock().lock(); driver.lock.writeLock().lock()
      val orderQueue = driver.getOrderQueue
      if (!orderQueue.remove(orderId)) {
        throw new Exception("Error - Order %s not actually assigned to driver %s" format (order.id, driver.id))
      }
      orderDao.assignTransaction(order.id, Order.Status.UNASSIGNED, null, driver.id, orderQueue.mkString(","))
      order.setDriverIdWithStatus(null, Order.Status.UNASSIGNED)
      driver.setOrderQueue(orderQueue)
    } finally {
      driver.lock.writeLock().unlock(); order.lock.writeLock().unlock()
    }
  }

  @throws(classOf[Exception])
  def reprioritize(orderId: Long, afterId: Long) {
    val order = this(orderId)
    val driver = driverManager(order.getDriverId)
    try {
      driver.lock.writeLock().lock()
      val queue = driver.getOrderQueue
      if (!queue.contains(orderId)) {
        throw new Exception("Error - Order %s is not assigned to driver %s" format (orderId, driver.id))
      }
      queue.remove(orderId)
      if (afterId < 0) {
        queue.add(orderId)
      } else {
        val pos = queue.indexOf(afterId)
        if (pos < 0) {
          throw new Exception("Error - afterId %s not in queue" format afterId)
        }
        queue.add(pos, orderId)
      }
      driverDao.updateOrderQueue(driver.id, queue.toList)
      driver.setOrderQueue(queue)
    } finally {
      driver.lock.writeLock().unlock()
    }
  }
}
