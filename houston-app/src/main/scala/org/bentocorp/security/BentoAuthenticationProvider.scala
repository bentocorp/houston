package org.bentocorp.security

import javax.annotation.PostConstruct

import org.bentocorp.db.{AdminUserDao, UserDao, DriverDao, IAuthDao}
import org.bentocorp.redis.{RedisCommands, Redis}
import org.redisson.client.RedisConnection
import org.redisson.client.codec.StringCodec
import org.redisson.client.protocol.{RedisCommands => RedissonRedisCommands}
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.security.authentication.{UsernamePasswordAuthenticationToken, AuthenticationProvider}
//import org.springframework.security.core.authority.SimpleGrantedAuthority
//import org.springframework.security.core.{GrantedAuthority, AuthenticationException, Authentication}
import org.springframework.stereotype.Component

import java.lang.{Integer => JInt}
import java.util.{List => JList, ArrayList}

// TODO - Should the authorization server be separate from the resource server?
@Component
class BentoAuthenticationProvider /* extends AuthenticationProvider */ {

  final val logger = LoggerFactory.getLogger(classOf[BentoAuthenticationProvider])

  protected val daos = scala.collection.mutable.Map.empty[String, IAuthDao]

  @Autowired
  def setDriverDao(dao: DriverDao) {
    daos += "d" -> dao
  }

  @Autowired
  def setUserDao(dao: UserDao) {
    daos += "c" -> dao
  }

  @Autowired
  def setAdminUserDao(dao: AdminUserDao) {
    daos += "a" -> dao
  }

  @Autowired
  var redis: Redis = _

  protected var redisConnection: RedisConnection = _

  @PostConstruct
  def init() {
    redisConnection = redis.redisClient.connect()
    // Tokens stored on Redis database 7
    val res0: String = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.SELECT, new JInt(Redis.AUTH_DB))
  }

  def cacheKeyAccessToken(clientId: String) = "accessToken_" + clientId

  def verifyToken(token: String): Boolean = {
    logger.debug("Verifying token " + token)
    val parts = token.split("-") // e.g. c-5678-0-ra8Ty09
    if (parts.length != 4) {
      logger.debug("Error - Malformed token " + token)
      return false
    }
    val clientType = parts(0)
    val primaryKey = parts(1).toLong
    val expiryTs = parts(2).toLong
    val clientId = clientType + "-" + primaryKey // c-5678
    // For now, always check the database when verifying access token
    daos(clientType).getTokenByPrimaryKey(primaryKey).contains(token)
    /*
    logger.debug("Retrieving API token from Redis for client " + clientId)
    val persistedToken: String = redisConnection.sync(StringCodec.INSTANCE, RedissonRedisCommands.GET, cacheKeyAccessToken(clientId))
    logger.debug("Got " + persistedToken)
    // TODO - Check expiry time?
    if (persistedToken == null || persistedToken.isEmpty || persistedToken != token) {
      // Check the database
      val tokenOpt: Option[String] = daos(clientType).getTokenByPrimaryKey(primaryKey)
      if (tokenOpt.isDefined) {
        // Update Redis
        val res0: String = redisConnection.sync(StringCodec.INSTANCE, RedisCommands.SET, cacheKeyAccessToken(clientId), tokenOpt.get)
      }
      tokenOpt.contains(token)
    } else {
      true
    }
    */
  }
}
