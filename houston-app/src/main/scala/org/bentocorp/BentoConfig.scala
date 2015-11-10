package org.bentocorp

import javax.annotation.PostConstruct

import com.typesafe.config.{ConfigValueFactory, Config, ConfigFactory}
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

object BentoConfig {
  final val Filename = "private-NO-COMMIT.conf"
}

@Component
class BentoConfig {

  final val Logger = LoggerFactory.getLogger(classOf[BentoConfig])

  // By default, SpringApplication converts any command line option (starting with "--") to a property and adds it to
  // the Environment instance before injection. Therefore, Houston must be started with the --env option in order to
  // load the correct configurations.
  @Autowired
  var springEnvironment: Environment = null

  var config: Config = null

  @PostConstruct
  def init() {
    val defaults = ConfigFactory.load(BentoConfig.Filename)
    val env = springEnvironment.getProperty("env")
    Logger.info("Loading configuration from %s for environment %s" format (BentoConfig.Filename, env))
    config = defaults.getConfig(env).withFallback(defaults)
    // Option to wipe Redis instance before running the Spring application
    val flushRedis = if (springEnvironment.getProperty("flush-redis") != null) true else false
    val noAuth = if (springEnvironment.getProperty("no-auth") != null) true else false
    config = config.withValue("flush-redis", ConfigValueFactory.fromAnyRef(flushRedis))
                   .withValue("no-auth", ConfigValueFactory.fromAnyRef(noAuth))
                   .withValue("env", ConfigValueFactory.fromAnyRef(env))
  }

  def toTypesafeConfig = config

  def getString(s: String) = config.getString(s)

  def getStringList(s: String) = config.getStringList(s)

  def getBoolean(s: String) = config.getBoolean(s)
}
