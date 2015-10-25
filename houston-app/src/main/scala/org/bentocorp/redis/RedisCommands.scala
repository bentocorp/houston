package org.bentocorp.redis

import org.redisson.client.protocol.RedisCommand
import org.redisson.client.protocol.decoder.StringReplayDecoder

object RedisCommands {
  final val MULTI: RedisCommand[String] = new RedisCommand[String]("MULTI", new StringReplayDecoder)
  final val GET: RedisCommand[String] = new RedisCommand[String]("GET", new StringReplayDecoder)
  final val SET: RedisCommand[String] = new RedisCommand[String]("SET", new StringReplayDecoder)
  final val SADD: RedisCommand[String] = new RedisCommand[String]("SADD", new StringReplayDecoder)
  final val SREM: RedisCommand[String] = new RedisCommand[String]("SREM", new StringReplayDecoder)
  final val DEL: RedisCommand[String] = new RedisCommand[String]("DEL", new StringReplayDecoder)
}
