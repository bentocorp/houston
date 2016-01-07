package org.bentocorp.redis

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.`type`.TypeFactory
import org.bentocorp.{Json, ScalaJson}
import org.redisson.client.RedisClient
import org.redisson.client.codec.StringCodec
import org.redisson.client.protocol.{RedisCommands => RedissonRedisCommands}
import org.slf4j.LoggerFactory

import scala.collection.mutable.{Map => MMap}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

import scala.collection.JavaConversions._

import java.util.{List => JList}
import java.lang.{Integer => JInt}

class RMap[K: Manifest, V: Manifest](redisClient: RedisClient, name: String) {

  val logger = LoggerFactory.getLogger(this.getClass)

  private def redisKeyMapEntry(key: String) = name + "_" + key

  @throws(classOf[Exception])
  def toMap: MMap[K, V] = {
    val redisConnection = redisClient.connect()
    try {
      var res0: String = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.SELECT, new JInt(Redis.DB))
      val result = MMap.empty[K, V]

      // Since you can't operate on data obtained in the middle of a transaction, a change in the map (specifically the
      // key set) can cause this function to fail due to inconsistent data such as values that do not exist (null) for
      // a given key. Consequently, we must use a Lua script (Redis executes the script transactionally).
      val delimiter = "_,,,_" // Try to use a unique delimiter
      val luaScript =
        s"""
          |local keys = redis.call('smembers', '$name')
          |local vals = { }
          |for i, k in ipairs(keys) do
          |  vals[i] = k .. '$delimiter' .. redis.call('get', '${name}_' .. k)
          |end
          |return vals
        """.stripMargin
      val ret: JList[String] = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.EVAL_LIST, luaScript, new JInt(0))

      ret foreach { str =>
        // First extract key/value from Redis string
        val parts = str.split(delimiter)
        if (parts.length != 2) {
          throw new Exception(s"Error trying to recreate map $name using Lua script on line $str")
        }
        val key = parts(0)
        val value = parts(1)
        // Then deserialize into objects
        val m = manifest[V]
        val parsed: V =
          if (m.typeArguments.isEmpty) {
            ScalaJson.parse(value, m.runtimeClass).asInstanceOf[V]
          } else {
            val parameterTypes = m.typeArguments.map(_.runtimeClass).toSeq
            val typeInfo = ScalaJson.TypeFactory.constructParametrizedType(m.runtimeClass, m.runtimeClass, parameterTypes: _*)
            ScalaJson.parse(value, typeInfo)
          }
        result += (key.asInstanceOf[K] -> parsed)
      }
      result
    } finally {
      redisConnection.closeAsync()
    }
  }

  def += (keyValue: (K, V)) {
    val redisConnection = redisClient.connect()
    var res0: String = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.SELECT, new JInt(Redis.DB))
    res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.MULTI)
    res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.SADD, name, keyValue._1+"")
    // Internally, always store as String -> String
    res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.SET, redisKeyMapEntry(keyValue._1+""), ScalaJson.stringify(keyValue._2))
    redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.EXEC)
    redisConnection.closeAsync() // TODO - Should be in a try/catch/finally block
  }

  def get(key: K): Option[V] = {
    val redisConnection = redisClient.connect()
    var res0: String = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.SELECT, new JInt(Redis.DB))
    val str: String = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.GET, redisKeyMapEntry(key + ""))
    redisConnection.closeAsync()
    if (str == null) {
      None
    } else {
      val obj = {
        val `type` = getJavaType(manifest[V])
//        println(str)
//        println(`type`)
        ScalaJson.parse(str, `type`).asInstanceOf[V]
      }
      Some(obj)
    }
  }

  def getJavaType(manifest: Manifest[_]): JavaType = {
    if (manifest.typeArguments.isEmpty) {
      return ScalaJson.TypeFactory.constructType(manifest.runtimeClass)
    }
    val typeArguments: Seq[JavaType] = manifest.typeArguments.map(getJavaType).toSeq
    ScalaJson.TypeFactory.constructParametrizedType(manifest.runtimeClass, manifest.runtimeClass, typeArguments: _*)
  }

  def apply(key: K): V = {
    val redisConnection = redisClient.connect()
    var res0: String = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.SELECT, new JInt(Redis.DB))
    val str = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.GET, redisKeyMapEntry(key+""))
    redisConnection.closeAsync()
    ScalaJson.parse(str, new TypeReference[V]() { })
  }

  def contains(key: K): Boolean = {
    get(key).isDefined
  }

  def remove(key: K) {
    val redisConnection = redisClient.connect()
    var res0: String = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.SELECT, new JInt(Redis.DB))
    res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.MULTI)
    res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.SREM, name, key+"")
    res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.DEL, redisKeyMapEntry(key+""))
    val results: JList[Object] = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.EXEC)
    redisConnection.closeAsync()
  }
}
