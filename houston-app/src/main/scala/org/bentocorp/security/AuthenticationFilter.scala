package org.bentocorp.security

import javax.servlet._

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AuthenticationFilter extends Filter {

  final val logger = LoggerFactory.getLogger(classOf[AuthenticationFilter])

  override def init(filterConfig: FilterConfig) {

  }

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    chain.doFilter(request, response)
  }

  override def destroy() {

  }
}
