package org.bentocorp.dispatch

import org.bentocorp.Order
import org.springframework.stereotype.Component

@Component
class OrderManager {
  // Initialize from database
  val orders = Map(
    500L -> new Order(500L, "Wayne Gretzky",   new Address("375 Valencia St", "San Francisco", "California", "USA", "94103"), "Test order"),
    501L -> new Order(501L, "Michael Jackson", new Address("2109 Mission St", "San Francisco", "California", "USA", "94110"), "Hi")
  )

  def getOrders: Array[Order] = orders.values.toArray

  def assign(orderId: Long, driverId: String): Order = {
    val o = orders(orderId)
    o.status = Order.Status.ACCEPTED
    o.driverId = driverId
    o
  }
}
