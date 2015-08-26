package com.bento.dispatch

import scala.collection.mutable

object DriverManager {

  private val _REGISTERED_DRIVERS = Array("marc", "vincent")

  private val _ONLINE_DRIVERS = mutable.Set.empty[String]

  def isRegistered(uid: String) = _REGISTERED_DRIVERS.contains(uid)

  def setDriverOnline(uid: String) = _ONLINE_DRIVERS += uid

  def setDriverOffline(uid: String) = _ONLINE_DRIVERS -= uid

  def getOnline = _ONLINE_DRIVERS.toArray
}
