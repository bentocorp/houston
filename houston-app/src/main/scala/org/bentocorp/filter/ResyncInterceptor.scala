package org.bentocorp.filter

import java.text.SimpleDateFormat
import java.util.{TimeZone, Calendar}
import javax.annotation.PostConstruct
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

import org.bentocorp.BentoConfig
import org.bentocorp.dispatch.{OrderManager, DriverManager}
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

  @volatile
  var lastResyncTs: Long = 0l

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
        lastResyncTs = resyncTs
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
