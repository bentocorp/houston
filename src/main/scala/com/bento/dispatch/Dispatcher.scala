package com.bento.dispatch

import scala.util.Random

object Dispatcher {
  private val _random = new Random
  // Simple mock
  def assign(order: String): String = {
    val online = DriverManager.getOnline
    online(_random.nextInt(online.length))
  }
}
