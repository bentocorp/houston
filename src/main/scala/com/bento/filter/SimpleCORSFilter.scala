package com.bento.filter

import javax.servlet._
import javax.servlet.http.HttpServletResponse

import org.springframework.stereotype.Component

// Cross-Origin Resource Sharing
@Component
class SimpleCORSFilter extends Filter {
  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
    res.asInstanceOf[HttpServletResponse].setHeader("Access-Control-Allow-Origin", "*")
    chain.doFilter(req, res)
  }

  def destroy() {
  }

  def init(filterConfig: FilterConfig) {
  }
}
