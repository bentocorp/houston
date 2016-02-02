package org.bentocorp.dispatch

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.{Calendar, TimeZone}
import javax.annotation.PostConstruct

import org.bentocorp._
import org.bentocorp.db._
import org.bentocorp.filter.ResyncInterceptor
import org.bentocorp.houston.config.BentoConfig
import org.bentocorp.houston.util.PhoneUtils
import org.bentocorp.redis.{RMap, Redis}
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import scala.collection.JavaConversions._
import scala.collection.mutable.{Map => MMap}

@Component
class OrderManager {

  final val Logger = LoggerFactory.getLogger(classOf[OrderManager])

  @Autowired
  var config: BentoConfig = null

  @Autowired
  var driverDao: DriverDao = _

  @Autowired
  var dishDao: DishDao = null

  @Autowired
  var orderDao: OrderDao = null

  @Autowired
  var genericOrderDao: GenericOrderDao = null

  @Autowired
  var driverManager: DriverManager = null

  @Autowired
  var redis: Redis = null

  var dishes: RMap[Long, BentoBox.Item] = null

  var orders: RMap[Long, Order[Bento]] = null

  var genericOrders: RMap[Long, Order[String]] = null

  final val TIME_ZONE = TimeZone.getTimeZone("PST")
  final val CALENDAR = Calendar.getInstance(TIME_ZONE)
  final val DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  DATE_FORMATTER.setTimeZone(TIME_ZONE)

  @PostConstruct
  def init() {
    dishes = redis.getMap[Long, BentoBox.Item]("dishes")
    orders = redis.getMap[Long, Order[Bento]]("orders")
    genericOrders = redis.getMap[Long, Order[String]]("genericOrders")
    // Load data into redis if we are the first thread to reach here
    val resyncTs = ResyncInterceptor.getClosestResyncTimeMillis(System.currentTimeMillis)
    redis.race("OrderManager#init_" + resyncTs, () => {
      syncOrders()
    })
  }

  def syncOrders() {
    // First, retrieve the menu
    val dishes = MMap.empty[Long, BentoBox.Item]
    Logger.info("Fetching dishes")
    dishDao.selectAll foreach {
      case (id, Some(name), Some(dishType), Some(label), Some(temp)) =>
        dishes += id -> new BentoBox.Item(id, name, dishType, label, temp)
      case row =>
        throw new Exception("Bad dish row - Expected (id, Some(name), Some(dishType), Some(label)) but got " + row)
    }
    Logger.info("Processed %s dishes" format dishes.size)
    redis.setMap("dishes", dishes)
    /* Bento orders */

    // Then retrieve all orders created on or after midnight of today
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("PST"))
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    // Note that the returned time (in milliseconds) corresponds to UTC - or time zone 0 - which is what is configured
    // in the databases
    val startOfToday = new Timestamp(calendar.getTimeInMillis)

    calendar.add(Calendar.DATE, 1)
    val endOfToday = new Timestamp(calendar.getTimeInMillis)

    Logger.info("Fetching orders created on or after %s (%s) and before %s (%s)" format (
      DATE_FORMATTER.format(startOfToday), startOfToday.getTime, DATE_FORMATTER.format(endOfToday), endOfToday.getTime
    ))

    // This will fetch all Order Ahead orders for today as well
    val orders = _createBentoOrdersFromMySql(orderDao.select(startOfToday, Some(endOfToday)))
    Logger.info("Processed %s Bento order(s)" format orders.size)

    /* generic orders */

    val genericOrders = MMap.empty[Long, Order[String]]
    Logger.info("Fetching generic orders created on or after %s (%s)" format (DATE_FORMATTER.format(startOfToday), startOfToday.getTime))
    genericOrderDao.select(startOfToday) foreach {
      // Note: Since "name" was split into "firstname" and "lastname", lastname may be null and that case must be
      // accounted for
      case (Some(orderId), statusStr, driverIdOpt, Some(firstname), lastnameOpt, Some(phone), Some(street), Some(city), Some(state), Some(zipCode), Some(country), lat, lng, Some(body), notesOpt) =>
        val status = Order.Status.parse(statusStr)
        if (status == Order.Status.CANCELLED) {
          Logger.debug("Ignoring %s order %s" format (status, orderId))
        } else {
          println("Order %s - %s" format ("g-"+orderId, driverIdOpt))
          val address = new Address(street, null, city, state, zipCode, country)
          if (lat.isDefined) {
            address.lat = lat.get.toFloat
          }
          if (lng.isDefined) {
            address.lng = lng.get.toFloat
          }
          val order = new Order[String]("g-" + orderId, firstname, lastnameOpt.orNull, phone, address, body)
          val driverId = if (driverIdOpt.isDefined && driverIdOpt.get > 0) new java.lang.Long(driverIdOpt.get) else new java.lang.Long(-1L)
          order.setDriverIdWithStatus(driverId, status)
          order.notes = notesOpt.getOrElse("")
          order.orderString = body
          genericOrders += (orderId -> order)
        }
      case row =>
        throw new Exception("Bad generic order row - " + row)
    }
    Logger.info("Processed %s generic order(s)" format genericOrders.size)
    // Transactionally set dishes, order, and genericOrders to distributed cache
    redis.setMap("orders", orders)
    redis.setMap("genericOrders", genericOrders)
  }

  private def _createBentoOrdersFromMySql(rows: List[OrderDao.CompleteOrderRow]): MMap[Long, Order[Bento]] = {
    // Build a map (as opposed to a list) of Bento orders because
    //   a) We can quickly look up existing orders as the Bentos are being built
    //   b) So that we can persist the data in Redis to be shared globally by multiple Houston instances
    val orders = MMap[Long, Order[Bento]]()
    rows foreach {
      case (orderId, Some(createdAt), Some(firstname), Some(lastname), Some(phone), Some(street), scheduledTimeZoneOpt, /*Some(city), Some(state),*/
      zipCodeOpt, Some(lat), Some(lng), mainOpt, side1Opt, side2Opt, side3Opt, side4Opt, Some(statusStr),
      driverIdOpt, notesOpt, Some(driverTextBlob), Some(orderType), scheduledWindowStartOpt, scheduledWindowEndOpt) =>
        val status = Order.Status.parse(statusStr)
        if (status == Order.Status.CANCELLED) {
          Logger.debug("Ignoring %s order %s" format (status, orderId))
        } else {
          val order = {
            orders.get(orderId) match {
              case Some(existingOrder) => existingOrder
              case _ =>
                // TODO - Create country enums?
                val address = new Address(street.trim(), null, "San Francisco", "California", zipCodeOpt.getOrElse(""), "United States")
                address.lat = lat.toFloat
                address.lng = lng.toFloat
                val newOrder = new Order[Bento]("o-" + orderId, firstname, lastname, PhoneUtils.normalize(phone), address, new Bento)
                val driverId = if (driverIdOpt.isDefined && driverIdOpt.get > 0) new java.lang.Long(driverIdOpt.get) else null
                newOrder.setDriverIdWithStatus(driverId, status)
                newOrder.notes = notesOpt.getOrElse("")
                newOrder.orderString = driverTextBlob
                newOrder.createdAt = createdAt.getTime
                // Order-Ahead stuff
                if (orderType == 2) {
                  newOrder.isOrderAhead = true
                  // For now, make scheduledWindowStart and scheduledWindowEnd mandatory if Order-Ahead (Routific does not require scheduledWindowEnd)
                  if (scheduledWindowStartOpt.isEmpty || scheduledWindowEndOpt.isEmpty) {
                    throw new Exception(s"$orderId is Order-Ahead but has either no delivery start window or delivery end window")
                  }
                  newOrder.scheduledWindowStart = scheduledWindowStartOpt.get.getTime
                  newOrder.scheduledWindowEnd = scheduledWindowEndOpt.get.getTime
                  newOrder.scheduledTimeZone = scheduledTimeZoneOpt.get
                }
                orders += orderId -> newOrder
                newOrder
            }
          }
          val bentoBoxItemIds: Array[Long] = Array(mainOpt, side1Opt, side2Opt, side3Opt, side4Opt).filter(_.isDefined).map(_.get)
          val bentoBox = new BentoBox
          bentoBoxItemIds foreach { id => bentoBox.add(dishes(id)) }
          // The order item here is a Bento (which is an Array of BentoBox)
          order.item.add(bentoBox)
        }
      case row =>
        throw new Exception("Bad order row - " + row)
    }
    orders
  }

  def createOrderAheadOrders(scheduledWindowStart: Timestamp, scheduledWindowEnd: Timestamp): MMap[Long, Order[Bento]] = {
    _createBentoOrdersFromMySql(orderDao.selectByDeliveryWindow(scheduledWindowStart, scheduledWindowEnd))
  }

  def getOrder(orderId: String): Order[_] = {
    val parts: Array[String] = orderId.split("-") // b-1234, g-5678
    if (parts.length < 2) {
      throw new Exception("Error - Malformed order identifier. Expected format is {type}-{primary_key} but got " + orderId)
    }
    val orderType = parts(0)
    val key = parts(1).toLong
    val orderOpt = orderType match {
      case "o" =>
        orders.get(key) match {
          case Some(o) =>
            Some(o)
          case _ =>
            // Load from database if not already in cache
            val bentos: MMap[Long, Order[Bento]] = _createBentoOrdersFromMySql(orderDao.selectByPrimaryKey(key))
            if (bentos.isEmpty) {
              None
            } else {
              val first = bentos.values.toList.head
              orders += key -> first
              Some(first)
            }
        }
      case "g" => genericOrders.get(key)
      case _ => throw new Exception("Error - Unrecognized order type trying to get order " + orderId)
    }
    if (orderOpt.isEmpty) {
      throw new Exception("Error - Order %s not found" format orderId)
    }
    orderOpt.get
  }

  @throws(classOf[Exception])
  def updateStatus(orderId: String, status: Order.Status): Order[_] = {
    // First, get the order to make sure it exists
    val order = getOrder(orderId)
    try {
      redis.lock(order.getLockId)
      // Write to the database
      val rowsAffected = order.getOrderType match {
        case "o" => orderDao.updateStatus(order.getOrderKey, status)
        case "g" => genericOrderDao.updateStatus(order.getOrderKey, status)
        case _ => throw new Exception("Error - OrderManager#updateStatus - " + orderId)
      }
      if(rowsAffected > 0) {
        order.setStatus(status)
        // Set to cache
        order.getOrderType match {
          case "o" => orders += (order.getOrderKey -> order.asInstanceOf[Order[Bento]])
          case "g" => genericOrders += (order.getOrderKey -> order.asInstanceOf[Order[String]])
        }
      } else {
        throw new Exception("Error updating status for order %s to %s" format (orderId, status))
      }
      order
    } finally {
      // Important!
      redis.unlock(order.getLockId)
    }
  }

  @Autowired
  var phpService: PhpService = null

  @throws(classOf[Exception])
  def assign(orderId: String, driverId: Long, afterId: String, token: String): Order[_] = {
    // First make sure the order and the driver exist
    val order = getOrder(orderId)
    val driver = driverManager.getDriver(driverId)
    if ("-1".equals(afterId)) {
      throw new Exception("Setting afterId to -1 has been deprecated. Use null instead")
    }
    try {
      // To mitigate deadlocks, obtain resources in a natural order - order first, then driver
      redis.lock(order.getLockId); redis.lock(driver.getLockId)
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
        case "o" =>
          if (!phpService.assign(orderId, driverId, afterId, token)) {
            throw new Exception("PHP order unassignment failed")
          }
          /*
          orderDao.assignOrderTransaction(
            Database.Map("pk_Order" -> key, "pk_Driver"->driverId, "order_queue"->q.mkString(",")),
            Order.Status.PENDING
          )
          */
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
      // Set to cache
      order.getOrderType match {
        case "o" => orders += (order.getOrderKey -> order.asInstanceOf[Order[Bento]])
        case "g" => genericOrders += (order.getOrderKey -> order.asInstanceOf[Order[String]])
      }
      driverManager.drivers += (driver.id -> driver)
      order
    } finally {
      // Important that we release all locks in reverse natural ordering
      redis.unlock(driver.getLockId); redis.unlock(order.getLockId)
    }
  }

  @throws(classOf[Exception])
  def unassign(orderId: String, token: String): Order[_] = {
    // First, make sure the order and driver exist
    val order = getOrder(orderId)
    val driver = driverManager.getDriver(order.getDriverId)
    // TODO - Move into try/catch/finally block after making sure that redis.unlock() is idempotent
    if (order.getStatus == Order.Status.COMPLETE) {
      throw new Exception("Error - You cannot unassign a completed order")
    }
    try {
      redis.lock(order.getLockId); redis.lock(driver.getLockId)
      val orderQueue = driver.getOrderQueue
      if (!orderQueue.remove(orderId)) {
        throw new Exception("Error - Order %s not actually assigned to driver %s" format (order.id, driver.id))
      }
      // Try updating database first
      order.getOrderType match {
        case "o" =>
          if (!phpService.assign(orderId, -1, "-1", token)) {
            throw new Exception("PHP order unassignment failed")
          }
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
      // Set to cache
      order.getOrderType match {
        case "o" => orders += (order.getOrderKey -> order.asInstanceOf[Order[Bento]])
        case "g" => genericOrders += (order.getOrderKey -> order.asInstanceOf[Order[String]])
      }
      driverManager.drivers += (driver.id -> driver)
      order
    } catch {
      case e: Exception =>
        throw e
    } finally {
      // TODO - Make sure redis.unlock() is idempotent
      redis.unlock(driver.getLockId); redis.unlock(order.getLockId)
    }
  }

  // TODO - This function belongs in DriverManager
  @throws(classOf[Exception])
  def reprioritize(orderId: String, afterId: String): Order[_] = {
    val order = getOrder(orderId)
    val driver = driverManager.getDriver(order.getDriverId)
    try {
      redis.lock(driver.getLockId)
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
        // Set to cache
        driverManager.drivers += (driver.id -> driver)
      } else {
        throw new Exception("Error reprioritizing %s before %s" format (orderId, afterId))
      }
      order
    } finally {
      redis.unlock(driver.getLockId)
    }
  }

  def delete(orderId: String, token: String) {
    val order = getOrder(orderId)
    val driverId = order.getDriverId
    if (driverId != null && driverId > 0) {
      throw new Exception("Error - Unassign the order first before deleting!")
    }
    try {
      redis.lock(order.getLockId)
      order.getOrderType match {
        case "o" =>
          if (!phpService.delete(orderId, token)) {
            throw new Exception("PHP order cancellation failed")
          }
          orders.remove(order.getOrderKey)
          /*
          if (orderDao.updateStatus(order.getOrderKey, Order.Status.CANCELLED) > 0) {
            orders.remove(order.getOrderKey)
          } else {
            throw new Exception("Error updating status to CANCELLED for " + orderId)
          }
          */
        case "g" =>
          if (genericOrderDao.updateStatus(order.getOrderKey, Order.Status.CANCELLED) > 0) {
            genericOrders.remove(order.getOrderKey)
          } else {
            throw new Exception("Error updating status to CANCELLED for " + orderId)
          }
      }
    } finally {
      redis.unlock(order.getLockId)
    }
  }

  @throws(classOf[Exception])
  def update(order: Order[_]) {
    // Keys must match column names in database!
    // First get values for columns common to both Bento and generic orders.
    var cols = Map(
      "phone"  -> order.phone,
      "city"   -> order.address.city,
      "state"  -> order.address.region,
      "zip"    -> order.address.zipCode,
      "lat"    -> order.address.lat.toString,
      "long"   -> order.address.lng.toString,
      "notes_for_driver" -> order.notes
    )

    // Try updating the database first. Then, if successful, set to cache.
    order.getOrderType match {
      case "o" =>
        // For Bento orders, we need to split street into street number and street name
        cols += "number" -> Address.extractNumberFromStreet(order.address.street).toString
        cols += "street" -> Address.extractNameFromStreet(order.address.street)
        if (orderDao.update(order.getOrderKey, cols) <= 0) {
          throw new Exception()
        }
        orders += (order.getOrderKey -> order.asInstanceOf[Order[Bento]])
      case "g" =>
        cols += "street" -> order.address.street
        if (genericOrderDao.update(order.getOrderKey, cols) <= 0) {
          throw new Exception()
        }
        genericOrders += (order.getOrderKey -> order.asInstanceOf[Order[String]])
      case _ =>
        throw new Exception()
    }
  }
}
