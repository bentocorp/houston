package org.bentocorp.dispatch

import com.fasterxml.jackson.databind.ObjectMapper
import org.bentocorp.Order
import org.bentocorp.controllers.HttpController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import scala.util.Random
import scala.collection.mutable

@Component
class Dispatcher {

  @Autowired(required = true)
  var _httpController: HttpController = _

  private val _mapper = new ObjectMapper

  private val _random = new Random
  // Simple mock
  def assign(order: String): String = {
    //val online = DriverManager.getOnline
    //online(0)
    "marc"
    //online(_random.nextInt(online.length))
  }


  // At startup, fetch all outstanding orders
  private final val _orders = mutable.HashMap[Long, Order[String]]()

  private def _stringify(o: Object) = {
    _mapper.writeValueAsString(o)
  }

  /*
  def processOrder(order: Order) {
    _orders += order.id -> order
    _assignmentQueue += order.id
    val res = _httpController.push("houston01", _stringify(Array("atlas01")), "NEW_ORDER", _stringify(order))
  }*/
/*
  def assign(orderId: Long, driverId: Long) {
    //_assignmentQueue -= orderId
    _acceptanceQueue += orderId
    val res = _httpController.push("houston01", _stringify(Array(driverId)), "NEW_ORDER", _stringify(_orders(orderId)))
  }*/

  def accept(orderId: Long, driverId: Long) = {

    //_httpController.push()
  }

  def reject(orderId: Long, driverId: Long) = {

  }

  def orderComplete(orderId: Long) = {

  }



  final val REMINDER_INTERVAL_MS = 1000 * 60 // 1 minute
  private final val _assignmentQueue = mutable.MutableList[Long]()
  private final val _acceptanceQueue = mutable.MutableList[Long]()

  val r = new Runnable {
    override def run {
      var head = _acceptanceQueue.headOption

      while (head.isDefined) {

        //_acceptanceQueue.remove(head.get._1)


        head = _acceptanceQueue.headOption
      }

    }
  }



}
