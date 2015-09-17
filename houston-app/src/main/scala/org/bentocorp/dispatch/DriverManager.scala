package org.bentocorp.dispatch

import org.bentocorp.Order
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.{RequestMapping, RestController}

import scala.collection.mutable

//@Component
object DriverManager {

  // Initialize from database
  private val _drivers = Map(
        800L -> new Driver(800L, "Jason Demant"    , "4153617728", Driver.Status.ONLINE ),
        801L -> new Driver(801L, "Joseph Lau"      , "4151234567", Driver.Status.ONLINE ),
        802L -> new Driver(802L, "Regina Grogan"   , "4151234567", Driver.Status.OFFLINE),
        803L -> new Driver(803L, "Alex Battaglia"  , "1876543209", Driver.Status.OFFLINE),
        804L -> new Driver(804L, "Daniel Seemann"  , "4159996767", Driver.Status.OFFLINE),
          1L -> new Driver(1L  , "Vincent Cardillo", "1234567890", Driver.Status.OFFLINE),
          8L -> new Driver(8L  , "Marc Doan"       , "1234567890", Driver.Status.OFFLINE)
  )
  _drivers(800L).orderQueue.add(603L)
  _drivers(800L).orderQueue.add(604L)
  _drivers(801L).orderQueue.add(600L)
  @throws(classOf[Exception])
  def apply(driverId: Long): Driver = _drivers.get(driverId) match {
    case Some(driver) => driver
    case None => throw new Exception("Driver %s not found" format driverId)
  }

  def is_registered(driverId: Long) = true

  def set_online(driverId: Long) {
    _drivers(driverId).status = Driver.Status.ONLINE
  }

  def set_offline(driverId: Long) {
    _drivers(driverId).status = Driver.Status.OFFLINE
  }

  def get = {

    _drivers.values.toArray
  }

}
