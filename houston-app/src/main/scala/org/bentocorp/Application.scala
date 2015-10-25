package org.bentocorp

import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.{EnableAutoConfiguration, SpringBootApplication}
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.{ComponentScan, Configuration}

object Application {
  final val LOGGER = LoggerFactory.getLogger(Application.getClass)
  // When starting as an embedded Tomcat server
  // java -jar target/houston-0.1.0.jar --env=dev
  def main(args: Array[String]) {
    val configuration: Array[Object] = Array(classOf[Application])
    val context: ApplicationContext = SpringApplication.run(configuration, args)
    LOGGER.debug("Hello, World!\n")
  }
}

@SpringBootApplication
class Application