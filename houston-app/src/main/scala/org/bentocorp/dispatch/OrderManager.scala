package org.bentocorp.dispatch

import java.sql.Timestamp
import javax.annotation.PostConstruct

import org.bentocorp.{Bento, BentoBox, Order}
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
  var genericOrderDao: GenericOrderDao = null

  @Autowired
  var driverManager: DriverManager = null

  var dishes = Map.empty[Long, BentoBox.Item]

  val orders = Map.empty[Long, Order[Bento]]
  val genericOrders = Map.empty[Long, Order[String]]

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
        } else {
          val order = {
            orders.get(orderId) match {
              case Some(existingOrder) => existingOrder
              case _ =>
                val address = new Address(number + " " + street, null, city, state, zipCode, "United States")
                address.lat = lat.toFloat
                address.lng = lng.toFloat
                val newOrder = new Order[Bento]("b-" + orderId, firstname + " " + lastname, phone, address, new Bento)
                val driverId = if (driverIdOpt.isDefined && driverIdOpt.get > 0) new java.lang.Long(driverIdOpt.get) else null
                newOrder.setDriverIdWithStatus(driverId, status)
                orders += orderId -> newOrder
                newOrder
            }
          }
          val bentoBox = (new BentoBox).add(dishes(main)).add(dishes(side1)).add(dishes(side2)).add(dishes(side3)).add(dishes(side4))
          // We don't need to use a write lock here because the order item generally won't be modified by dispatcher threads
          order.item.add(bentoBox)
        }
      case row =>
        val error = "Bad order row - " + row
        println(error)
        throw new Exception(error)
    }
    /* generic orders */
    val genericOrderRows = genericOrderDao.select(today)
    Logger.debug("Fetched %s generic order(s) created on or after %s" format (genericOrderRows.length, today))
    genericOrderRows foreach {
      case (Some(orderId), statusStr, driverIdOpt, Some(name), Some(phone), Some(street), Some(city), Some(state), Some(zipCode), Some(country), lat, lng, Some(body)) =>
        val status = Order.Status.parse(statusStr)
        if (status == Order.Status.CANCELLED) {
          Logger.debug("Ignoring %s order %s" format (status, orderId))
        } else {
          val address = new Address(street, null, city, state, zipCode, country)
          if (lat.isDefined) {
            address.lat = lat.get.toFloat
          }
          if (lng.isDefined) {
            address.lng = lng.get.toFloat
          }
          val order = new Order[String]("g-" + orderId, name, phone, address, body)
          val driverId = if (driverIdOpt.isDefined && driverIdOpt.get > 0) new java.lang.Long(driverIdOpt.get) else null
          order.setDriverIdWithStatus(driverId, status)
          genericOrders += (orderId -> order)
        }
      case row =>
        val error = "Error - corrupted generic order row - " + row
        println(error)
        throw new Exception(error)
    }
  }

  def getOrder(orderId: String): Order[_] = {
    val parts: Array[String] = orderId.split("-") // b-1234, g-5678
    if (parts.length < 2) {
      throw new Exception("Error - Malformed order identifier. Expected format is {type}-{primary_key} but got " + orderId)
    }
    val orderType = parts(0)
    val key = parts(1).toLong
    val orderOpt = orderType match {
      case "b" => orders.get(key)
      case "g" => genericOrders.get(key)
      case _ => throw new Exception("Error - Unrecognized order type trying to get order " + orderId)
    }
    if (orderOpt.isEmpty) {
      throw new Exception("Error - Order %s not found" format (orderId))
    }
    orderOpt.get
  }

  @throws(classOf[Exception])
  def updateStatus(orderId: String, status: Order.Status) = {
    // First, get the order to make sure it exists
    val order = getOrder(orderId)
    try {
      order.lock.writeLock().lock()
      // Write to the database
      val rowsAffected = order.getOrderType match {
        case "b" => orderDao.updateStatus(order.getOrderKey, status)
        case "g" => genericOrderDao.updateStatus(order.getOrderKey, status)
        case _ => throw new Exception("Error - OrderManager#updateStatus - " + orderId)
      }
      if(rowsAffected > 0) {
        order.setStatus(status)
      } else {
        throw new Exception("Error updating status for order %s to %s" format (orderId, status))
      }
    } finally {
      order.lock.writeLock().unlock()
    }
  }

  @throws(classOf[Exception])
  def assign(orderId: String, driverId: Long, afterId: String = null) {
    // First make sure the order and the driver exist
    val order = getOrder(orderId)
    val driver = driverManager.getDriver(driverId)
    try {
      // To mitigate deadlocks, obtain resources in a natural order - order first, then driver
      order.lock.writeLock().lock(); driver.lock.writeLock().lock()
      // Check if order already assigned
      val cd = order.getDriverId
      if (cd != null && cd > 0) {
        throw new Exception("Error - Order %s is currently assigned to driver %s" format (orderId, cd))
      }
      val q = driver.getOrderQueue
      if (afterId == null || afterId.isEmpty) {
        q.add(orderId)
      } else {
        val pos = q.indexOf(afterId)
        if (pos < 0) {
          throw new Error("Error - could not find afterId %s in queue" format afterId)
        }
        q.add(pos, orderId)
      }
      // Try updating database first
      val parts = orderId.split("-")
      val orderType = parts(0)
      val key = parts(1)
      orderType match {
        case "b" =>
          orderDao.assignOrderTransaction(
            Database.Map("pk_Order" -> key, "pk_Driver"->driverId, "order_queue"->q.mkString(",")),
            Order.Status.PENDING
          )
        case "g" =>
          genericOrderDao.assignOrderTransaction(
            Database.Map("pk_generic_Order" -> key, "pk_Driver"->driverId, "order_queue"->q.mkString(",")),
            Order.Status.PENDING
          )
        case _ => throw new Exception("Error - OrderManager#assign - Unrecognized order type for %s" format orderId)
      }
      // Finally update everything else
      order.setDriverIdWithStatus(driverId, Order.Status.PENDING)
      driver.setOrderQueue(q)
    } finally {
      // Important that we release all locks in reverse natural ordering
      driver.lock.writeLock().unlock(); order.lock.writeLock().unlock()
    }
  }

  @throws(classOf[Exception])
  def unassign(orderId: String) {
    // First, make sure the order and driver exist
    val order = getOrder(orderId)
    val driver = driverManager.getDriver(order.getDriverId)
    try {
      order.lock.writeLock().lock(); driver.lock.writeLock().lock()
      val orderQueue = driver.getOrderQueue
      if (!orderQueue.remove(orderId)) {
        throw new Exception("Error - Order %s not actually assigned to driver %s" format (order.id, driver.id))
      }
      // Try updating database first
      order.getOrderType match {
        case "b" =>
          orderDao.assignOrderTransaction(
            Database.Map("pk_Order" -> order.getOrderKey, "pk_Driver" -> driver.id, "order_queue" -> orderQueue.mkString(",")),
            Order.Status.UNASSIGNED
          )
        case "g" =>
          genericOrderDao.assignOrderTransaction(
            Database.Map("pk_generic_Order" -> order.getOrderKey, "pk_Driver" -> driver.id, "order_queue" -> orderQueue.mkString(",")),
            Order.Status.UNASSIGNED
          )
        case _ => throw new Exception("Error - OrderManager#unassign - Unrecognized order type for %s" format orderId)
      }
      // Finally update everything else
      order.setDriverIdWithStatus(null, Order.Status.UNASSIGNED)
      driver.setOrderQueue(orderQueue)
    } finally {
      driver.lock.writeLock().unlock(); order.lock.writeLock().unlock()
    }
  }

  @throws(classOf[Exception])
  def reprioritize(orderId: String, afterId: String) {
    val order = getOrder(orderId)
    val driver = driverManager.getDriver(order.getDriverId)
    try {
      driver.lock.writeLock().lock()
      val queue = driver.getOrderQueue
      if (!queue.contains(orderId)) {
        throw new Exception("Error - Order %s is not assigned to driver %s" format (orderId, driver.id))
      }
      queue.remove(orderId)
      if (afterId == null || afterId.isEmpty) {
        queue.add(orderId)
      } else {
        val pos = queue.indexOf(afterId)
        if (pos < 0) {
          throw new Exception("Error - afterId %s not in queue" format afterId)
        }
        queue.add(pos, orderId)
      }
      // First write to database
      if (driverDao.updateOrderQueue(driver.id, queue.toList) > 0) {
        driver.setOrderQueue(queue)
      } else {
        throw new Exception("Error reprioritizing %s before %s" format (orderId, afterId))
      }
    } finally {
      driver.lock.writeLock().unlock()
    }
  }

  def delete(orderId: String) {
    val order = getOrder(orderId)
    val driverId = order.getDriverId
    if (driverId != null && driverId > 0) {
      throw new Exception("Error - Unassign the order first before deleting!")
    }
    try {
      order.lock.writeLock().lock()
      order.getOrderType match {
        case "b" =>
          if (orderDao.updateStatus(order.getOrderKey, Order.Status.CANCELLED) > 0) {
            orders.remove(order.getOrderKey)
          } else {
            throw new Exception("Error updating status to CANCELLED for " + orderId)
          }
        case "g" =>
          if (genericOrderDao.updateStatus(order.getOrderKey, Order.Status.CANCELLED) > 0) {
            genericOrders.remove(order.getOrderKey)
          } else {
            throw new Exception("Error updating status to CANCELLED for " + orderId)
          }
      }
    } finally {
      order.lock.writeLock().unlock()
    }
  }
}
