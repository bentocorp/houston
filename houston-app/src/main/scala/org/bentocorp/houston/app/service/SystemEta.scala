package org.bentocorp.houston.app.service

import java.lang.{Integer => JInt, Long => JLong}
import java.util.UUID
import javax.annotation.PostConstruct

import org.bentocorp.db.{DriverDao, SettingsDao}
import org.bentocorp.houston.config.BentoConfig
import org.bentocorp.houston.util.HttpUtils
import org.bentocorp.redis.{RMap, Redis}
import org.bentocorp.{ScalaJson, Bento, Order}
import org.redisson.client.{RedisTimeoutException, RedisConnection}
import org.redisson.client.codec.StringCodec
import org.redisson.client.protocol.RedisCommands
import org.slf4j.{Logger, LoggerFactory}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

object SystemEta {
  final val KEY_SSE_MINUTES_MULTIPLIER = "sse_minutesMultiplier"
  final val KEY_SSE_RESULT = "sse_result"

  final val UPDATE_INTERVAL_MS = 1000*1//1000 * 60 * 5 // 5 minutes

  final val QUEUE_NAME = "queue_system-eta"

  final val ROUND_TO_NEAREST_MINUTE = 5
}

@Component
class SystemEta {

  val logger: Logger = LoggerFactory.getLogger(classOf[SystemEta])

  @Autowired
  var redis: Redis = null

  var orders: RMap[Long, Order[Bento]] = null
  var genericOrders: RMap[Long, Order[String]] = null

//  var drivers: RMap[Long, Driver] = null

  @Autowired
  var config: BentoConfig = _

  val uuid: String = UUID.randomUUID.toString

  var token: String = null

  @PostConstruct
  def init() {
    // Use DB #9 - Distributed coordination
    val redisConnection: RedisConnection = redis.connect(9)
    try {
      if (!config.getIsNull("deploy-id")) {
        val deployId = config.getString("deploy-id")
        // If this service is being initialized as part of a new deployment, race to see if we are the first distributed
        // instance to reach here. If yes, we are responsible for seeding the task queue.
        val race = "race_system-eta#" + deployId

        val place: JLong = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.INCR, race)

        if (place <= 1) {
          logger.debug("First place in race " + race + " - Initializing " + SystemEta.QUEUE_NAME)
          // Seed the task queue
          var res0: Object = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.RPUSH, SystemEta.QUEUE_NAME, new JInt(0))
          // Expire the race key in 5 minutes
          res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.EXPIRE, race, new JInt(60*5))
        } else {
          logger.debug(place + " in race " + race)
        }
      } else {
        logger.debug("Not part of a new deployment so not racing")
      }

      orders = redis.getMap[Long, Order[Bento]]("orders")
      genericOrders = redis.getMap[Long, Order[String]]("genericOrders")
//      drivers = redis.getMap[Long, Driver]("drivers")
    } catch {
      case e: Exception => logger.error(e.getMessage, e)
    } finally {
      redisConnection.closeAsync()
    }
  }

  @Autowired
  var driverDao: DriverDao = null

  @Autowired
  var settingsDao: SettingsDao = null

  def updateSystemEta() {
    logger.debug("Updating system ETA")
    val multiplier: Double = settingsDao.select(SystemEta.KEY_SSE_MINUTES_MULTIPLIER) match {
      case (_, Some(value), _, _, _) => value.toDouble
      case _ => throw new Exception("Error - Unable to fetch multiplier from database")
    }
    var driverCount: Double = driverDao.selectAllOnShift.size
    if (driverCount <= 0) driverCount = 1

    val orderCount: Double = (orders.toMap.map(_._2.getStatus) ++ genericOrders.toMap.map(_._2.getStatus)).foldLeft(0.0) {
      case (prev, status) =>
        if (status != Order.Status.COMPLETE && status != Order.Status.CANCELLED) {
          prev + 1.0
        } else {
          prev
        }
    }

    var averageOrdersPerDriver: Double = orderCount/driverCount

    // Must be at least 1
    averageOrdersPerDriver = if (averageOrdersPerDriver <= 1) 1.0 else averageOrdersPerDriver

    val res: Int = roundToNearestMinutes(averageOrdersPerDriver * multiplier, SystemEta.ROUND_TO_NEAREST_MINUTE)
    logger.debug("orderCount=%s, driverCount=%s, average=%s, multiplier=%s, SSE=%s" format (
      orderCount, driverCount, averageOrdersPerDriver, multiplier, res
    ))
    if (settingsDao.update(SystemEta.KEY_SSE_RESULT, res.toString) <= 0) {
      throw new Exception("Error - Problem persisting SSE=%s to MySQL" format res)
    }
    val str = HttpUtils.get(
      config.getString("node.url") + "/api/push",
      Map("from" -> "houston", "to" -> "\"atlas\"", "subject" -> "sse_update",
        "body" -> res, "token" -> token))
  }

  def roundToNearestMinutes(d: Double, minute: Int): Int = {
    ((d + minute/2.0)/minute).toInt * minute
  }

  var thread: Thread = null

  @volatile
  var continue = false

  def stop() {
    logger.debug("Stopping System ETA service")
    continue = false
  }

  def start(token: String): Boolean = {
    this.token = token
    if (thread != null && thread.isAlive) {
      logger.info("System ETA service is already running!")
      return false
    }
    val runnable = new Runnable {
      override def run() {
        val redisConnection = redis.connect(9)
        var start = 0L
        try {
          while (continue) {
            logger.debug("Waiting in line")
            start = System.currentTimeMillis
            // 0 - Block indefinitely until data becomes available
            var res0: Object = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.BLPOP, SystemEta.QUEUE_NAME, new JInt(0))

            res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.SET, "current-actor_system-eta", uuid)

            updateSystemEta()

            logger.debug("Sleeping " + (SystemEta.UPDATE_INTERVAL_MS/1000.0) + " seconds")
            Thread.sleep(SystemEta.UPDATE_INTERVAL_MS)

            val actor: String = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.GET, "current-actor_system-eta")
            if (actor == uuid) {
              logger.debug("Writing data to " + SystemEta.QUEUE_NAME + " to unblock next consumer in line")
              res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.LPUSH, SystemEta.QUEUE_NAME, new JInt(0))
            } else {
              // If, when we are done, we discover that another service instance was active then we let them be
              // responsible for writing to the task queue
              logger.debug("current-actor_system-eta (%s) does not match my UUID (%s) so not writing to task queue" format (actor, uuid))
            }
          }
        } catch {
          case timeoutException: RedisTimeoutException =>
            val dt = (System.currentTimeMillis - start)/1000.0
            logger.error(timeoutException.getMessage + " - dt: " + dt + " sec.", timeoutException)
          case e: Exception => logger.error(e.getMessage, e)
        } finally {
          redisConnection.closeAsync()
        }
        logger.debug("System ETA service stopped")
      }
    }

    continue = true
    thread = new Thread(runnable)
    logger.debug("Starting System ETA service")
    thread.start()
    true
  }

  def getSimpleSystemEta: Int = {
    settingsDao.select(SystemEta.KEY_SSE_RESULT) match {
      case (_, Some(value), _, _, _) => value.toInt
      case _ => throw new Exception("Error - Problem fetching `settings`.`%s` from database" format SystemEta.KEY_SSE_RESULT)
    }
  }
}
