package org.bentocorp.redis

import javax.annotation.PostConstruct

import org.bentocorp.ScalaJson
import org.bentocorp.houston.config.BentoConfig
import org.redisson.client.codec.StringCodec
import org.redisson.client.protocol.decoder.StringReplayDecoder
import org.redisson.client.{RedisClient, RedisConnection}
import org.redisson.connection.RandomLoadBalancer
import org.redisson.{Config, Redisson}
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import scala.collection.JavaConversions._
import scala.collection.mutable.{Map => MMap}

import java.util.{List => JList}
import java.lang.{Integer => JInt}

import org.redisson.client.protocol.{RedisCommands => RedissonRedisCommands}

object Redis {
  final val AUTH_DB = 7
  final val DB = 8 // Use database #8
}

@Component
class Redis {

  final val Logger = LoggerFactory.getLogger(classOf[Redis])

  @Autowired
  var config: BentoConfig = null

  private var redisClient: RedisClient = null

  var redisson: Redisson = null

  @PostConstruct
  def init() {
    // Use Redisson as Distributed Lock Manager
    val master = config.getString("redis.master")
    val slaves = config.getStringList("redis.slaves")
    Logger.info("Initializing redis client with master %s and slaves %s" format (master, slaves.mkString(",")))
    val redisConfig = new Config()
    redisConfig.useMasterSlaveConnection()
               .setMasterAddress(master)
               .setLoadBalancer(new RandomLoadBalancer)
               .addSlaveAddress(slaves: _*)
               .setDatabase(Redis.DB) // Use database #8
    redisson = Redisson.create(redisConfig)
    // Use low-level Redis client for everything else
    val parts: Array[String] = master.split(":")
    val host = parts(0)
    val port = parts(1).toInt
    redisClient = new RedisClient(host, port)
    if (config.getBoolean("flush-redis")) {
      Logger.info("!!ATTENTION!! Flushing databases 8 & 9")
      flushdb(List(8,9))
    }
  }

  def connect(db: Int): RedisConnection = {
    val redisConnection = redisClient.connect()
    val res0: String = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.SELECT, new JInt(db))
    redisConnection
  }

  private def redisKeyRaceQueue(key: String) = "race-queue_" + key

  def race(key: String, celebrate: () => Unit) {
    val redisConnection = connect(9)
    var place: Long = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.INCR, key)
    if (place == 1) {
      // This thread is first in the race
      Logger.info("First place in %s - celebrating" format key)
      celebrate()
    } else {
      // Otherwise, block indefinitely (timeout=0) until the winner finishes celebrating
      Logger.info("Blocked at position %s in race %s" format (place, key))
      val res1: Object = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.BLPOP, redisKeyRaceQueue(key), new java.lang.Integer(0))
    }
    //place = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.DECR, key)
    //if (place > 0) {
      Logger.info("Finished - passing baton for race " + key)
      // Unblock the next thread waiting in line
      val res2: java.lang.Long = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.RPUSH, redisKeyRaceQueue(key), "OK")
    //} else {
      // Delete keys to prevent memory leaks?
    //}
    redisConnection.closeAsync()
  }

  def getMap[K: Manifest, V: Manifest](name: String) = {
    new RMap[K, V](redisClient, name)
  }

  def setMap(name: String, map: MMap[_, _]) {
//    println("Redis#setMap - %s\n%s" format (name, map))
    val redisConnection: RedisConnection = redisClient.connect()
    var res0: String = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.SELECT, new JInt(Redis.DB))
    res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.MULTI)
//    println("MULTI -> " + res0)
    map foreach {
      case (key, value) =>
        // Tricky business mixing Java generics with Scala =(
        // We must save the return value otherwise Scala will incorrectly perform an implicit type cast
        res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.SADD, name, key+"")
        //println("SET %s %s" format (name + "_" + key, ScalaJson.stringify(value)))
        res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.SET, name + "_" + key, ScalaJson.stringify(value))
    }
    val res: JList[Object] = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.EXEC)
    //res foreach println
    redisConnection.closeAsync()
  }

  // Use the same Redisson instance everywhere in the application
  // Locks are re-entrant
  def lock(key: String) {
    val lock = redisson.getLock("redis-lock_" + key)
    lock.lock()
  }

  def unlock(key: String) {
    val lock = redisson.getLock("redis-lock_" + key)
    lock.unlock()
  }

  def flushdb(dbs: List[Int]) {
    val redisConnection = redisClient.connect()
    try {
      var res0: Object = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.MULTI)
      dbs foreach { i =>
        res0 = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.SELECT, new JInt(i))
        res0 = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.FLUSHDB)
      }
      val res1: JList[Object] = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.EXEC)
    } finally {
      redisConnection.closeAsync()
    }
  }
}
