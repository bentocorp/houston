package org.bentocorp.dispatch


import org.bentocorp.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.{RequestMapping, RestController}
import scala.collection.mutable
//@Component
object OrderManager {
  var id = 799
  def next_id() = {
    id = id + 1; id
  }
  // Initialize from database
  val orders = mutable.Map(
    600L -> new Order(600L, "Wayne Gretzky",   new Address("375 Valencia St",  "", "San Francisco", "California", "USA", "94103"), "Test order"),
    601L -> new Order(601L, "Michael Jackson", new Address("2109 Mission St",  "", "San Francisco", "California", "USA", "94110"), "Hi"),
    602L -> new Order(602L, "Eric O'Donnell",  new Address("488 Battery St",   "", "San Francisco", "California", "USA", "94111"), "Wassup"),
    603L -> new Order(603L, "Sidney Crosby",   new Address("2017 Mission St",  "", "San Francisco", "California", "USA", "94110"), "Yo"),
    604L -> new Order(604L, "Buffy Summers",   new Address("427 Stockton St",  "", "San Francisco", "California", "USA", "94108"), "Omg"),
    605L -> new Order(605L, "Alex Battaglia",  new Address("25 Divisadero Ave","", "San Francisco", "California", "USA", "94117"), "Hey there"),
    606L -> new Order(606L, "Quentin Taratino",new Address("1 Market St",      "", "San Francisco", "California", "USA", "94105"), "Horror")
  )
  orders(600).status = Order.Status.ACCEPTED
  orders(600).driverId = 801L
  orders(601).status = Order.Status.COMPLETE
  orders(601).driverId = 801L
  orders(603).status = Order.Status.MODIFIED
  orders(603).driverId = 800L
  orders(604).status = Order.Status.REJECTED
  orders(604).driverId = 800L
  @throws(classOf[Exception])
  def apply(orderId: Long): Order[String] = orders.get(orderId) match {
    case Some(order) => order
    case None => throw new Exception("Order %s not found" format orderId)
  }

  def get: mutable.Map[Long, Order[String]] = {
    orders
  }

  /**
   * When we move an order around, there are 3 things we must do successfully to prevent a bad state:
   *   a) update the order's assigned driver (Order#driverId)
   *   b) if already assigned, remove the order from the current driver's order queue (Driver#orderQueue)
   *   c) if applicable, insert the order into the new driver's order queue
   *  @param orderId  (required) identifier for the order being moved
   *         driverId -1 means un-assignment
   *         afterId  -1 means insert at end
   */
  @throws(classOf[Exception])
  def move(orderId: Long, driverId: Long, afterId: Long): Order[String] = {
    val order = OrderManager(orderId) // error if order doesn't exist
    // If the order is currently assigned, we must be able to successfully modify its current priority
    var cp = -1 // current priority (if any)
    var cq: java.util.List[java.lang.Long] = null
    if (order.driverId != null && order.driverId >= 0) {
      cq = DriverManager(order.driverId).orderQueue // error if the order's assigned driver does not exist
      cp = cq.indexOf(orderId)
      if (cp < 0) {
        throw new Error("Error - Order %s is currently assigned to driver %s but it is not in his/her order queue" format (orderId, driverId))
      }
    }
    var np = -1 // new priority
    var nq: java.util.List[java.lang.Long] = null
    if (driverId >= 0) {
      val driver = DriverManager(driverId) // error if driver does not exist
      if (driver.status != Driver.Status.ONLINE) {
        throw new Error("Error - driver %s is not online" format driverId)
      }
      nq = driver.orderQueue
      if (afterId >= 0) {
        np = nq.indexOf(afterId) // error if afterId >= 0 but driver doesn't exist
        if (np < -1) {
          throw new Exception("After order %s is not assigned to driver %s" format (afterId, driverId))
        }
      }
    }
    // {{ If we reach here, it should be safe to proceed. It's important that the following statements all execute successfully
    if (cp >= 0) {
      cq.remove(cp)
    }
    if (driverId < 0) {
      order.status = Order.Status.UNASSIGNED
    } else {
      order.driverId = driverId
      if (order.status == Order.Status.UNASSIGNED) {
        order.status = Order.Status.PENDING
      }
      if (np >= 0) {
        nq.add(np, orderId)
      } else {
        nq.add(orderId)
      }
    }
    // }}
    order
  }

  @throws(classOf[Exception])
  def reprioritize() {

  }

  @throws(classOf[Exception])
  def unassign() {

  }
}
