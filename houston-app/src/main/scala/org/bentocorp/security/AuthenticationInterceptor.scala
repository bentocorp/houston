package org.bentocorp.security

import javax.annotation.PostConstruct
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.bentocorp.houston.config.BentoConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter

@Component
class AuthenticationInterceptor extends HandlerInterceptorAdapter {

  final val logger = LoggerFactory.getLogger(classOf[AuthenticationInterceptor])

  @Autowired
  var bentoAuthenticationProvider: BentoAuthenticationProvider = null

  @Autowired
  var config: BentoConfig = _

  var noAuth = false

  @PostConstruct
  def init() {
    // If the application was started with the "--no-auth" option, accept all tokens
    if (config.getBoolean("no-auth")) {
      logger.info("The application was started with the \"--no-auth\" option. This interceptor will accept all tokens.")
      noAuth = true
    }
  }

  // Intercept any request for a protected resource
  override def preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Object): Boolean = {
    if (noAuth) {
      return true
    }
    val url = request.getRequestURL.toString
    logger.debug("Intercepted request for resource " + url)
    val token = request.getParameter("token")
    if (token == null || token == "") {
      response.sendError(400, "Error - Missing access token in request URL")
      false
    } else if (!bentoAuthenticationProvider.verifyToken(token)) {
      response.sendError(401, "Error - Invalid access token")
      false
    } else {
      // If we get here, the token should be well-formed and valid
      val parts = token.split("-")
      request.setAttribute("token", token)
      request.setAttribute("clientId", parts(0) + "-" + parts(1))
      true
    }
  }
}
