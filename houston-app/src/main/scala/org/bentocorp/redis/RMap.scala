package org.bentocorp.redis

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.`type`.TypeFactory
import org.bentocorp.{Json, ScalaJson}
import org.redisson.client.RedisClient
import org.redisson.client.codec.StringCodec
import org.redisson.client.protocol.{RedisCommands => RedissonRedisCommands}

import scala.collection.mutable.{Map => MMap}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

import scala.collection.JavaConversions._

import java.util.{List => JList}
import java.lang.{Integer => JInt}

class RMap[K: Manifest, V: Manifest](redisClient: RedisClient, name: String) {

  val redisConnection = redisClient.connect()
  val res0: String = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.SELECT, new JInt(Redis.DB))

  private def redisKeyMapEntry(key: String) = name + "_" + key

  @throws(classOf[Exception])
  def toMap: MMap[K, V] = {
    val result = MMap.empty[K, V]
    val keys: JList[String] = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.SMEMBERS, name)
//    println("RMap(%s)#toMap - %s" format (name, keys.size))
    // Since you can't operate on data obtained in the middle of a transaction, this method will throw an Exception if
    // a change in the map is detected after the key set has been retrieved
    var res0: String = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.MULTI)
    for (key <- keys) {
      res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.GET, redisKeyMapEntry(key))
    }
    val values: JList[String] = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.EXEC)
//    println("GET count(res0)=%s" format values.size)
    val k = keys.iterator
    val v = values.iterator
    while (v.hasNext) {
      val key = k.next()
      val value = v.next()
      if (value == null) {
        throw new Exception("Error - got null value for key " + key + " from Redis")
      }

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
  }

  def += (keyValue: (K, V)) {
    var res0: String = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.MULTI)
    res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.SADD, name, keyValue._1+"")
    // Internally, always store as String -> String
    res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.SET, redisKeyMapEntry(keyValue._1+""), ScalaJson.stringify(keyValue._2))
    redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.EXEC)
  }

  def get(key: K): Option[V] = {
    val str: String = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.GET, redisKeyMapEntry(key + ""))
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
    val str = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.GET, redisKeyMapEntry(key+""))
    ScalaJson.parse(str, new TypeReference[V]() { })
  }

  def contains(key: K): Boolean = {
    get(key).isDefined
  }

  def remove(key: K) {
    var res0: String = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.MULTI)
    res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.SREM, name, key+"")
    res0 = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.DEL, redisKeyMapEntry(key+""))
    val results: JList[Object] = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.EXEC)
  }
}
