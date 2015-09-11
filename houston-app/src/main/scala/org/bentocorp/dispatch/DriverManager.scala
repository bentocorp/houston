package org.bentocorp.dispatch

object DriverManager {
  // Initialize from database
  private val _REGISTERED_DRIVERS = Map(
       "marc" -> new Driver(   "marc",        "Marc Doan", "4153617728", Driver.Status.OFFLINE),
    "vincent" -> new Driver("vincent", "Vincent Cardillo", "4151234567", Driver.Status.OFFLINE),
        "702" -> new Driver(    "702",     "Jason Demant", "4151234567", Driver.Status.OFFLINE),
        "703" -> new Driver(    "703",       "Joseph Lau", "1876543209", Driver.Status.OFFLINE),
        "704" -> new Driver(    "704",   "Daniel Seemann", "4159996767", Driver.Status.OFFLINE)
  )

  def isRegistered(uid: String) = _REGISTERED_DRIVERS.contains(uid)

  def setDriverOnline(uid: String) {
    _REGISTERED_DRIVERS(uid).status = Driver.Status.ONLINE
  }

  def setDriverOffline(uid: String) {
    _REGISTERED_DRIVERS(uid).status = Driver.Status.OFFLINE
  }

  def getDrivers = _REGISTERED_DRIVERS.values.toArray
}
