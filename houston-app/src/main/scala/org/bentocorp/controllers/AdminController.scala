package org.bentocorp.controllers

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.TimeZone

import org.bentocorp.Order
import org.bentocorp.api.APIResponse._
import org.bentocorp.db.{Database, TOrder, TOrderStatus}
import org.bentocorp.houston.config.BentoConfig
import org.bentocorp.houston.util.{EmailUtils, TimeUtils}
import org.bentocorp.redis.{Redis, RedisCommands}
import org.redisson.client.RedisConnection
import org.redisson.client.codec.StringCodec
import org.redisson.client.protocol.{RedisCommands => RedissonRedisCommands}
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.{RequestMapping, RequestParam, RestController}

import scala.slick.driver.MySQLDriver.simple._
import scala.slick.lifted.TableQuery

@RestController
@RequestMapping(Array("/admin"))
class AdminController {

  val logger = LoggerFactory.getLogger(classOf[AdminController])

  @Autowired
  var redis: Redis = null

  @Autowired
  var config: BentoConfig = null

  @Autowired
  var database: Database = _

  @RequestMapping(Array("/mockOrderAhead"))
  def mockOrderAhead(@RequestParam("start") startStr: String,
                     @RequestParam("end")   endStr  : String,
                     @RequestParam(value = "mod" , defaultValue = "1") mod : Int,
                     @RequestParam(value = "dt"  , defaultValue = "0") dt  : String,
                     @RequestParam(value = "exec", defaultValue = "false") exec: Boolean): String = {

    val orderTable = TableQuery[TOrder]

    val orderStatusTable = TableQuery[TOrderStatus]

    val formatStr = "yyyy-MM-dd HH:mm"
    val start = TimeUtils.parseTimestamp(startStr, formatStr, "PST") // assume PST
    val end = TimeUtils.parseTimestamp(endStr, formatStr, "PST") // assume PST

    val res = new StringBuffer("=)<br/>")

    database() withSession { implicit session =>
      val orders = orderTable.filter(r => r.created_at >= start && r.created_at < end).list

      val f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z")
      f.setTimeZone(TimeZone.getTimeZone("PST"))

      val offset: Long = dt.toInt * (1000L * 60 * 60 * 24) // offset in days

      orders foreach { order =>
        if (order._1 % mod == 0) {
          val full: Long = 3600000L//1800000L
          val half: Long = full/2
          // round created_at to the nearest 30 minutes
          val windowStart = new Timestamp(
            (((order._3.get.getTime + half) / full) * full) + offset
          )
          val windowEnd = new Timestamp(windowStart.getTime + full) // each window is 30 minutes long

          // Sometimes the rounding will result in windows outside of regular shift hours so we have to ignore those
          val wT = TimeUtils.getLocalTime(windowEnd.getTime, TimeZone.getTimeZone("PST"))
          val eT = TimeUtils.getLocalTime(end.getTime, TimeZone.getTimeZone("PST"))

          if (wT.equals(eT) || wT.isBefore(eT)) {

            res.append("%s: %s -> %s, %s" format (order._1, f.format(order._3.get), f.format(windowStart), f.format(windowEnd)))

            if (exec) {

              orderTable.filter(_.pk_Order === order._1)
                        .map(r => (r.order_type, r.scheduled_timezone, r.scheduled_window_start, r.scheduled_window_end))
                        .update(Some(2), Some("America/Los_Angeles"), Some(windowStart), Some(windowEnd))

              orderStatusTable.filter(_.fk_Order === order._1)
                              .map(r => (r.fk_Driver, r.status))
                              .update((Some(-1), Some(Order.Status.UNASSIGNED.toString)))

              res.append(" *EXECUTED*")
            }
            res.append("<br/>")
          }
        }
      }
    }
    res.toString
  }

  def key(deviceId: String, property: String) = {
    "admin_" + deviceId + "-" + property
  }

  @RequestMapping(Array("/setForcedUpdateInfo"))
  def fn0(@RequestParam(value = "device_id") deviceId: String,
          @RequestParam(value = "min_version"    , defaultValue = "") minVersion   : String,
          @RequestParam(value = "min_version_url", defaultValue = "") minVersionUrl: String): String = {
    var redisConnection: RedisConnection = null
    try {
      redisConnection = redis.connect(1)
      var res0: String = ""
      // TODO - Which database to use!?
      if (!minVersion.isEmpty)
        res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.SET, key(deviceId, "min_version"), minVersion)
      if (!minVersionUrl.isEmpty)
        res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.SET, key(deviceId, "min_version_url"), minVersionUrl)
      success("OK")
    } catch {
      case e: Exception =>
        logger.error(e.getMessage, e.getStackTrace)
        error(1, e.getMessage)
    } finally {
      if (redisConnection != null) redisConnection.closeAsync()
    }
  }

  @RequestMapping(Array("/getForcedUpdateInfo"))
  def bar(@RequestParam(value = "device_id") deviceId: String) = {
    var redisConnection: RedisConnection = null
    try {
      redisConnection = redis.connect(1)
      var res = new java.util.HashMap[String, String]()
      var res0: String = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.GET, key(deviceId, "min_version"))
      res.put("min_version", res0)
      res0 = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.GET, key(deviceId, "min_version_url"))
      res.put("min_version_url", res0)
      success(res)
    } catch {
      case e: Exception =>
        logger.error(e.getMessage, e.getStackTrace)
        e.printStackTrace()
        error(1, e.getMessage)
    } finally {
      if (redisConnection != null) redisConnection.closeAsync()
    }
  }

    @RequestMapping(Array("/reportAtlasException"))
    def reportAtlasException(@RequestParam("html") html: String) = {
        try {
            // TODO - Move logic to EmailUtils because it is also being used in GlobalControllerExceptionHandler
            var from = "engalert"
            val env: String = config.getString("env")
            if ("prod" == env) {
                from += "@bentonow.com"
            } else {
                from += s"-${env}@bentonow.com"
            }
            EmailUtils.send("api",
                            config.getString("mailgun.key"),
                            from,
                            "email@bento.pagerduty.com",
                            "EXCEPTION: Atlas",
                            html)
            success("OK")
        } catch {
            case exc: Exception =>
                exc.printStackTrace()
                error(1, exc.getMessage)
        }
    }

}
