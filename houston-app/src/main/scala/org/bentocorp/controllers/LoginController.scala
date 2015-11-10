package org.bentocorp.controllers

import org.bentocorp.api.APIResponse._
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.{RestController, RequestParam, RequestMapping}

@RestController
@RequestMapping(Array("/api"))
class LoginController {

  final val logger = LoggerFactory.getLogger(classOf[LoginController])

  @RequestMapping(Array("/authenticate"))
  def authenticate(@RequestParam("username") username: String,
                   @RequestParam("password") password: String,
                   @RequestParam("type")     `type`  : String) = {
    logger.debug("LoginController#authenticate(%s, %s, %s)" format (username, password, `type`))
    // This does not need to be implemented now because all clients currently authenticate with either Node or PHP
    error(1, "Sorry, authentication is currently not supported in Houston =(")
  }
}
