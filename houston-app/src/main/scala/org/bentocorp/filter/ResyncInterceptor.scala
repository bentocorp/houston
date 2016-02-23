package org.bentocorp.filter

import java.lang.{Integer => JInt}
import java.text.SimpleDateFormat
import java.util.{Calendar, TimeZone}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import org.bentocorp.dispatch.{DriverManager, OrderManager}
import org.bentocorp.redis.Redis
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter

object ResyncInterceptor {

  final val formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  final val timeZone = TimeZone.getTimeZone("PST")

  formatter.setTimeZone(timeZone)

  val scheduledResyncTimes = Array(
    ( 0,  0, 0, 0), // Midnight
    (16, 30, 0, 0)  // 4:30 PM (half an hour before dinner starts)
  )

  def getClosestResyncTimeMillis(ts: Long): Long = {
    val calendar = Calendar.getInstance(timeZone)
    scheduledResyncTimes.reverseIterator foreach { el =>
      calendar.set(Calendar.HOUR_OF_DAY, el._1)
      calendar.set(Calendar.MINUTE     , el._2)
      calendar.set(Calendar.SECOND     , el._3)
      calendar.set(Calendar.MILLISECOND, el._4)
      val candidate = calendar.getTimeInMillis
      if (ts >= candidate) {
        return candidate
      }
    }
    calendar.getTimeInMillis
  }
}

@Component
class ResyncInterceptor extends HandlerInterceptorAdapter {

  final val logger = LoggerFactory.getLogger(classOf[ResyncInterceptor])

  // Very important - This field must be initialized because DriverManager and OrderManager will have already
  // performed the initial resync at instantiation. If set to 0, ResyncInterceptor will trigger an additional (and
  // unnecessary) resync which will have the following effect
  //    + The cache will be flushed, but
  //    + The key for the current @code{resyncTs} will be greater than 1 (because this will have already been
  //      incremented past 1 by DriverManager and OrderManager when those classes were instantiated)
  //    + Therefore, the drivers and orders will not be reloaded
  @volatile
  var lastResyncTs: Long = ResyncInterceptor.getClosestResyncTimeMillis(System.currentTimeMillis)

  @Autowired
  var redis: Redis = _

  @Autowired
  var orderManager: OrderManager = _

  @Autowired
  var driverManager: DriverManager = _

  import ResyncInterceptor._

  override def preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Object): Boolean = {
    val now = System.currentTimeMillis
    val resyncTs = getClosestResyncTimeMillis(now)
    logger.debug("lastResyncTs=%s, currentTimeMillis=%s, resyncTs=%s" format (
      formatter.format(lastResyncTs),
      formatter.format(now),
      formatter.format(resyncTs))
    )
    // Each HTTP request is processed in a separate thread and so multiple threads may race to update
    // @code{lastResyncTs}
    this.synchronized {
      if (lastResyncTs != resyncTs) {
        lastResyncTs = resyncTs // TODO - What to do with existing
        logger.debug("Flushing database 8")
        redis.flushdb(List(8))
        logger.debug("Resyncing orders")
        redis.race("OrderManager#init_" + resyncTs, () => { orderManager.syncOrders() })
        logger.debug("Resyncing drivers")
        redis.race("DriverManager#init_" + resyncTs, () => { driverManager.syncDrivers() })
        logger.debug("Finished resyncing =)")
      }
    }
    true
  }
}
