package org.bentocorp

import javax.annotation.PostConstruct

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class Config {

  final val Logger = LoggerFactory.getLogger(classOf[Config])

  val filename = "private-NO-COMMIT.conf"

  @Autowired
  var E: Environment = null

  var config: com.typesafe.config.Config = null

  @PostConstruct
  def init() {
    val defaults = ConfigFactory.load(filename)
    val e = E.getProperty("env")
    Logger.info("Loading configuration from %s for environment %s" format (filename, e))
    config = defaults.getConfig(e).withFallback(defaults)
  }

  def apply() = config
}
