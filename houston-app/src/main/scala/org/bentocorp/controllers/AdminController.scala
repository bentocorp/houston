package org.bentocorp.controllers

import javax.annotation.PostConstruct

import org.bentocorp.ScalaJson
import org.bentocorp.houston.config.BentoConfig
import org.bentocorp.redis.{RedisCommands, Redis}
import org.redisson.client.{RedisClient, RedisConnection}
import org.redisson.client.codec.StringCodec
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{RequestParam, RequestMapping, RestController}
import org.redisson.client.protocol.{RedisCommands => RedissonRedisCommands}

import org.bentocorp.api.APIResponse._

@RestController
@RequestMapping(Array("/admin"))
class AdminController {

  val logger = LoggerFactory.getLogger(classOf[AdminController])

  @Autowired
  var redis: Redis = null

  @Autowired
  var config: BentoConfig = null

  def key(deviceId: String, property: String) = {
    "admin_" + deviceId + "-" + property
  }

  @RequestMapping(Array("/setForcedUpdateInfo"))
  def fn0(@RequestParam(value = "device_id") deviceId: String,
          @RequestParam(value = "min_version"    , defaultValue = "") minVersion   : String,
          @RequestParam(value = "min_version_url", defaultValue = "") minVersionUrl: String): String = {
    var redisClient: RedisClient = null
    var redisConnection: RedisConnection = null
    try {
      val masterUrl = config.getString("redis.master")
      val parts = masterUrl.split(":")
      redisClient = new RedisClient(parts(0), parts(1).toInt)
      redisConnection = redisClient.connect()
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
      if (redisClient != null) redisClient.shutdownAsync()
    }
  }

  @RequestMapping(Array("/getForcedUpdateInfo"))
  def bar(@RequestParam(value = "device_id") deviceId: String) = {
    var redisClient: RedisClient = null
    var redisConnection: RedisConnection = null
    try {
      val masterUrl = config.getString("redis.master")
      val parts = masterUrl.split(":")
      println(parts(0))
      println(parts(1).toInt)
      redisClient = new RedisClient(parts(0), parts(1).toInt)
      redisConnection = redis.redisClient.connect()
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
      if (redisClient != null) redisClient.shutdownAsync()
    }
  }
}
