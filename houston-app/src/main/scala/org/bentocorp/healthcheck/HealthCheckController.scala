package org.bentocorp.healthcheck

import org.springframework.http.{HttpStatus, ResponseEntity}
import org.springframework.web.bind.annotation.{RequestMapping, RestController}

@RestController
class HealthCheckController {
  @RequestMapping(Array("/ping"))
  def ping = {
    new ResponseEntity[String]("pong\n", HttpStatus.OK)
  }
}
