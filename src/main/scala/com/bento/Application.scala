package com.bento

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.{EnableAutoConfiguration, SpringBootApplication}
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.{ComponentScan, Configuration}

object Application {
  def main(args: Array[String]) {
    val configuration: Array[Object] = Array(classOf[Application])
    val context: ApplicationContext = SpringApplication.run(configuration, args)
    println("Hello, World!\n")
  }
}

@SpringBootApplication
class Application